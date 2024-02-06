package com.example.airquality

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.airquality.databinding.ActivityEnergyBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import org.json.JSONException
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class EnergyActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val user = FirebaseAuth.getInstance().currentUser
    private val userId = user?.uid
    private lateinit var binding: ActivityEnergyBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var LightsCountView: TextView
    private val database = FirebaseDatabase.getInstance()
    var EnergySum : Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnergyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setButtonListeners()

        auth = FirebaseAuth.getInstance()

        LightsCountView = findViewById(R.id.LightsCountView)

        if (userId != null) {
            fetchFromFirebase(userId) {
                getUserAccessTokenAndLoadLightsInfo()
            }
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.parent_layout)
        val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)

        rootLayout.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!isPointInsideView(event.rawX, event.rawY, relativeLayout)) {
                    relativeLayout.visibility = View.GONE
                }
            }
            false
        }
        updateEstimatedEnergySum()
    }

    fun interpolate(bri: Int): Double {
        val brightnessLevels = arrayOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100)
        val powerLevels = arrayOf(0.5, 1.0, 1.1, 1.2, 1.4, 1.6, 1.8, 2.1, 2.4, 2.8, 3.2, 3.6, 4.1, 4.6, 5.1, 5.7, 6.3, 6.9, 7.5, 8.2, 8.9)

        for (i in 0 until brightnessLevels.size - 1) {
            if (bri <= brightnessLevels[i + 1]) {
                val fraction = (bri - brightnessLevels[i]).toDouble() / (brightnessLevels[i + 1] - brightnessLevels[i])
                return powerLevels[i] + fraction * (powerLevels[i + 1] - powerLevels[i])
            }
        }
        return powerLevels.last()  // 최대 밝기 값인 경우
    }


    private fun getUserAccessTokenAndLoadLightsInfo() {
        val myRef = userId?.let { database.getReference("users").child(it) }

        myRef?.child("accessToken")?.get()?.addOnSuccessListener { accessTokenSnapshot ->
            val accessToken = accessTokenSnapshot.value as? String
            if (!accessToken.isNullOrEmpty()) {
                getLightsInfo(accessToken)
            }
        }?.addOnFailureListener { e ->
            e.printStackTrace()
        }
    }

    private fun getLightsInfo(accessToken: String) {
        val myRef = userId?.let { database.getReference("users").child(it) }

        myRef?.child("bridgeApiKey")?.get()?.addOnSuccessListener { apiKeySnapshot ->
            val apiKey = apiKeySnapshot.value as? String
            if (!apiKey.isNullOrEmpty()) {
                val request = Request.Builder()
                    .url("https://api.meethue.com/bridge/$apiKey/lights/")
                    .get()
                    .header("Authorization", "Bearer $accessToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            try {
                                // Now that the lights info is saved, fetch and create buttons
                                fetchLightsAndCreateButtons(userId!!) // Pass the JSON object to the function
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        } else {
                            println("Error: ${response.code}")
                        }
                    }
                })
            }
        }?.addOnFailureListener { e ->
            e.printStackTrace()
        }
    }

    private fun fetchLightsAndCreateButtons(userId: String) {
        val layout = findViewById<LinearLayout>(R.id.linear_layout_for_lights)
        val lightsRef = database.getReference("users").child(userId).child("lights")

        lightsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (lightSnapshot in dataSnapshot.children) {
                    val lightNumber = lightSnapshot.key ?: continue
                    val isOn = lightSnapshot.child("on").getValue(Boolean::class.java)
                    val bri = lightSnapshot.child("bri").getValue(Long::class.java)?.toString() ?: "N/A"
                    val turnOnTime = lightSnapshot.child("turnOntime").getValue(String::class.java) ?: "N/A"
                    val turnOnTimeMillisString = lightSnapshot.child("turnOntimeMillis").getValue(String::class.java)
                    val usedPower = lightSnapshot.child("usedPower").getValue(Double::class.java) ?: 0.0

                    if (isOn == true) {
                        val lightName = "Light $lightNumber"
                        val turnOnTimeMillis = turnOnTimeMillisString?.toLongOrNull()

                        if (turnOnTimeMillis != null) {
                            val currentTimestamp = System.currentTimeMillis()
                            val timeDifferenceMillis = currentTimestamp - turnOnTimeMillis
                            val hours = timeDifferenceMillis / (1000 * 60 * 60).toDouble()

                            val normalizedBri = (bri.toDouble() / 254.0) * 100
                            val estimatedPower = interpolate(normalizedBri.toInt())
                            val estimatedUsedPower = estimatedPower * hours

                            // 업데이트된 usedPower를 계산하여 Firebase에 업데이트
                            val newUsedPower = usedPower + estimatedUsedPower
                            lightSnapshot.child("usedPower").ref.setValue(newUsedPower)
                            createLightButton(layout, lightName, turnOnTime, bri, newUsedPower)
                        }
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d("Debug", "Database error: $databaseError")
            }
        })
    }


    private fun updateEstimatedEnergySum() {
        val formattedEnergySum = String.format("%.1f", EnergySum)
        binding.estimatedPowerView.text = "시간당 예상 소비 전력 총량 : $formattedEnergySum W"
    }

    private fun fetchFromFirebase(userId: String, onComplete: (() -> Unit)? = null) {
        val userRef = database.getReference("users").child(userId).child("lights")

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                var lightsCount = 0
                for (lightSnapshot in dataSnapshot.children) {
                    val isOn = lightSnapshot.child("on").getValue(Boolean::class.java)
                    if (isOn == true) {
                        lightsCount++
                    }
                }

                runOnUiThread {
                    LightsCountView.text = "현재 사용중인 조명: $lightsCount"
                }

                onComplete?.invoke()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun createLightButton(layout: LinearLayout, lightName: String, turnOnTime: String, bri: String, usedpower: Double) {
        // 0~254 범위의 bri 값을 0~100 범위로 정규화
        val normalizedBri = (bri.toDouble() / 254.0) * 100
        val estimatedPower = interpolate(normalizedBri.toInt())
        val estimatedLessPower = String.format("%.1f", estimatedPower - interpolate((normalizedBri/2).toInt()))
        val usedPower = String.format("%.2f", usedpower)
        EnergySum += estimatedPower

        // 전체 버튼을 포함할 LinearLayout
        val buttonContainer = LinearLayout(this@EnergyActivity)
        buttonContainer.orientation = LinearLayout.HORIZONTAL

        // Light ID 부분
        val lightIdTextView = TextView(this@EnergyActivity)
        lightIdTextView.text = "$lightName\n 현재 소비 전력량: $usedPower W\n조도 감소 예상 절감량: $estimatedLessPower W"
        lightIdTextView.textSize = 15F
        lightIdTextView.gravity = Gravity.CENTER
        lightIdTextView.setBackgroundColor(Color.WHITE)
        lightIdTextView.setTextColor(Color.GRAY)
        lightIdTextView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

        // turnOnTime과 bri 표시 부분
        val lightDetailsTextView = TextView(this@EnergyActivity)
        lightDetailsTextView.text = "$turnOnTime\nBri: $bri\nPower: $estimatedPower W"
        lightDetailsTextView.textSize = 20F
        lightDetailsTextView.setBackgroundColor(Color.WHITE)
        lightDetailsTextView.setTextColor(Color.GRAY)
        lightDetailsTextView.gravity = Gravity.CENTER
        lightDetailsTextView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)


        buttonContainer.addView(lightIdTextView)
        buttonContainer.addView(lightDetailsTextView)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            convertDpToPx(100)
        )
        params.setMargins(16, 16, 16, 16)
        buttonContainer.layoutParams = params

        buttonContainer.tag = lightName.split(" ")[1]

        buttonContainer.setOnClickListener {
            val intent = Intent(this@EnergyActivity, MenuActivity::class.java)
            intent.putExtra("Light_Id", it.tag.toString())
            startActivity(intent)
        }
        layout.addView(buttonContainer)
        updateEstimatedEnergySum()
    }

    private fun convertDpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        return (x > viewX && x < (viewX + view.width)) &&
                (y > viewY && y < (viewY + view.height))
    }

    private fun setButtonListeners() {
        val tabButton = binding.tabButton
        tabButton.setOnClickListener {
            val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)
            relativeLayout.visibility = View.VISIBLE

            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, Tab1Fragment())
            fragmentTransaction.commit()
        }
    }
}