package com.example.airquality

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.airquality.databinding.ActivityLightsMenuBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject
import android.graphics.Color
import android.util.TypedValue
import com.example.airquality.data.User
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import org.json.JSONException
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

class LightsMenuActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private val user = FirebaseAuth.getInstance().currentUser
    private val userId = user?.uid
    private lateinit var binding: ActivityLightsMenuBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameView: TextView
    private lateinit var bridgeIPView: TextView
    private lateinit var apiKeyView: TextView
    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setButtonListeners()

        auth = FirebaseAuth.getInstance()

        usernameView = findViewById(R.id.usernameView)
        bridgeIPView = findViewById(R.id.BridgeIPView)
        apiKeyView = findViewById(R.id.APIkeyView)

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

        val bridgeRegisterButton = findViewById<MaterialButton>(R.id.bridge_register_button)
        bridgeRegisterButton.setOnClickListener {
            // 인증 액티비티로 이동
            val intent = Intent(this, OauthActivity::class.java)
            startActivity(intent)
        }
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
                            val responseBody = response.body?.string()
                            try {
                                val lightsJson = JSONObject(responseBody)

                                // Save the lights info to Firebase
                                saveLightsInfoToFirebase(lightsJson)

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

    private fun saveLightsInfoToFirebase(lightsJson: JSONObject) {
        val myRef = userId?.let { database.getReference("users").child(it).child("lights") }

        val lightNumbers = lightsJson.keys().asSequence().toList()
        for (lightNumber in lightNumbers) {
            val lightInfo = lightsJson.getJSONObject(lightNumber).getJSONObject("state")

            val lightData = HashMap<String, Any>()

            myRef?.child(lightNumber)?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val existingData = snapshot.value as? HashMap<String, Any> ?: HashMap()

                    // Extract and store required light information
                    lightData["bri"] = lightInfo.getInt("bri")
                    lightData["hue"] = lightInfo.getInt("hue")
                    lightData["on"] = lightInfo.getBoolean("on")
                    lightData["sat"] = lightInfo.getInt("sat")
                    val xyArray = lightInfo.getJSONArray("xy")
                    lightData["x"] = xyArray.getDouble(0)
                    lightData["y"] = xyArray.getDouble(1)
                    lightData["lightId"] = lightNumber // 추가: lightId도 저장

                    // turnOntime 항목을 유지
                    existingData["turnOntime"]?.let {
                        lightData["turnOntime"] = it
                    }

                    // 이전에 저장된 Mycolor 정보를 유지
                    existingData["Mycolor"]?.let {
                        lightData["Mycolor"] = it
                    }

                    existingData["MyBri"]?.let {
                        lightData["MyBri"] = it
                    }

                    // 기존 데이터와 새 데이터를 병합
                    existingData.putAll(lightData)

                    // 병합된 데이터를 Firebase에 저장
                    myRef?.child(lightNumber)?.setValue(existingData)?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("Debug", "Light $lightNumber information saved to Firebase")
                        } else {
                            Log.e("Error", "Failed to save light $lightNumber information: ${task.exception}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Error", "Database error while fetching existing data: $error")
                }
            })
        }
    }


    private fun fetchLightsAndCreateButtons(userId: String) {
        val layout = findViewById<LinearLayout>(R.id.linear_layout_for_lights)

        val lightsRef = database.getReference("users").child(userId).child("lights")
        println("light: $")
        lightsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (lightSnapshot in dataSnapshot.children) {
                    val lightNumber = lightSnapshot.key ?: continue
                    val lightName = "Light $lightNumber" // Change this to the actual light name if available
                    println("light: $lightName")
                    createLightButton(layout, lightName, lightNumber)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.d("Debug", "Database error: $databaseError")
            }
        })
    }

    private fun fetchFromFirebase(userId: String, onComplete: (() -> Unit)? = null) {
        val userRef = database.getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val user = dataSnapshot.getValue(User::class.java)
                if (user != null) {
                    runOnUiThread {
                        usernameView.text = "Hello, ${user.username}"
                        bridgeIPView.text = getDisplayText("Token", user.accessToken)
                        apiKeyView.text = getDisplayText("API Key", user.bridgeApiKey)
                    }
                }
                onComplete?.invoke()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun createLightButton(layout: LinearLayout, lightName: String, lightId: String) {
        val lightButton = Button(this@LightsMenuActivity)
        lightButton.text = lightName
        lightButton.tag = lightId
        lightButton.textSize = 30F
        lightButton.setBackgroundColor(Color.WHITE)
        lightButton.setTextColor(Color.GRAY)
        lightButton.setPadding(16, 16, 16, 16)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            convertDpToPx(100)
        )
        params.setMargins(16, 16, 16, 16)
        lightButton.layoutParams = params

        lightButton.setOnClickListener {
            val intent = Intent(this@LightsMenuActivity, MenuActivity::class.java)
            intent.putExtra("Light_Id", it.tag.toString()) // "Light_Id"를 Intent에 추가
            startActivity(intent)
        }
        layout.addView(lightButton)
    }



    private fun getDisplayText(prefix: String, value: String?): String {
        return "$prefix: ${value ?: "not set"}"
    }


    private fun convertDpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
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
