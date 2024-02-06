package com.example.airquality

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.airquality.databinding.ActivityMenuBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class MenuActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMenuBinding
    private lateinit var lightId: String
    private var hueUsername: String? = null
    private var access_token: String? = null
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var myBri: Int? = null
    private lateinit var popupManager: PopupManager
    private var lightStateListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Light_Id를 문자열로 가져옴
        lightId = intent.getStringExtra("Light_Id") ?: "1"
        binding.lightView.text = "Light No.$lightId"

        val httpClient = getUnsafeOkHttpClient()
        popupManager = PopupManager(this, httpClient, lightId)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userId = currentUser.uid

            // 이제 userId를 사용하여 데이터베이스에서 값을 불러올 수 있습니다.
            database.child("users").child(userId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    hueUsername = dataSnapshot.child("bridgeApiKey").getValue(String::class.java) ?: ""
                    myBri = dataSnapshot.child("MyBri").getValue(Int::class.java) ?: 0
                    access_token = dataSnapshot.child("accessToken").getValue(String::class.java) ?: ""
                    // 데이터베이스에서 값을 가져온 후 초기화를 처리
                    postInitialization()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // Handle errors here
                }
            })
        } else {
            // 사용자가 로그인하지 않았을 때의 처리
        }

        val rootLayout = binding.root
        val relativeLayout = binding.relativeLayout

        val settingsPopup = PopupWindow(this)
        val popupView = layoutInflater.inflate(R.layout.popup_setting, null)
        settingsPopup.contentView = popupView

        val settingButton = binding.settingButton
        settingButton.setOnClickListener {
            showSettingsPopup(it)
        }

        // 루트 레이아웃에 터치 리스너 설정
        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!isPointInsideView(event.rawX, event.rawY, relativeLayout)) {
                    relativeLayout.visibility = View.GONE
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        getCurrentLightState()
    }

    private fun getCurrentLightState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            lightStateListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val currentBrightness = dataSnapshot.getValue(Int::class.java)
                    if (currentBrightness != null) {
                        if (currentBrightness > 1) {
                            binding.levelview.text = "현재 밝기: $currentBrightness"
                        } else if (currentBrightness == 1 ){
                            binding.levelview.text = "현재 조명이 꺼져있습니다."
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // 데이터를 읽는데 실패한 경우 처리
                }
            }
            database.child("users").child(userId).child("lights").child(lightId).child("bri").addValueEventListener(lightStateListener!!)
        }
    }



    private fun postInitialization() {
        setButtonListeners()
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
        val buttons = listOf(
            binding.lv1btn to 1,
            binding.lv2btn to 2,
            binding.lv3btn to 3,
            binding.lv4btn to 4,
        )

        for ((button, level) in buttons) {
            button.setOnClickListener {
                // 리스너 제거
                val currentUser = auth.currentUser
                if (currentUser != null && lightStateListener != null) {
                    val userId = currentUser.uid
                    database.child("users").child(userId).child("lights").child(lightId).child("bri").removeEventListener(lightStateListener!!)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    setLightState(true, level)
                }
            }
        }
        binding.tabButton.setOnClickListener {
            val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)
            relativeLayout.visibility = View.VISIBLE

            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, Tab1Fragment())
            fragmentTransaction.commit()
        }

        binding.mylvbtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userId = currentUser.uid
                    try {
                        val snapshot = database.child("users").child(userId).child("lights").child("$lightId").child("MyBri").get().await()
                        println("MyBri = $myBri")
                        myBri = snapshot.getValue(Int::class.java) ?: 0
                    } catch (e: Exception) {
                        // 에러 처리
                    }
                }

                // 가져온 myBri 값을 사용하여 불을 켜거나 변경합니다.
                setLightState(true, -1)
            }
        }

        binding.turnOff.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                setLightState(false, 0)
            }
        }

    }

    private suspend fun setLightState(isOn: Boolean, level: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val firebaseDatabase = FirebaseDatabase.getInstance()
        val lightReference = firebaseDatabase.getReference("users/$userId/lights/$lightId")

        if (userId != null) {
            val currentDataSnapshot = lightReference.get().await()
            val currentIsOn = currentDataSnapshot.child("on").getValue(Boolean::class.java)
            val pastBri: Int? = currentDataSnapshot.child("bri").getValue(Int::class.java)

            if (isOn == true && currentIsOn == false) {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val turnOnTime = sdf.format(Date()) // 현재 시간을 HH:mm:ss 형식으로 가져옵니다.
                lightReference.child("turnOntime").setValue(turnOnTime)
                val turnOnTimeMillis = System.currentTimeMillis().toString()
                lightReference.child("turnOntimeMillis").setValue(turnOnTimeMillis)

                // 새로운 코드: usedPower를 0.0으로 초기화
                lightReference.child("usedPower").setValue(0.0)
            } else if (isOn == true && currentIsOn == true && pastBri != null) {
                val turnOnTimeMillisString = currentDataSnapshot.child("turnOntimeMillis").getValue(String::class.java)
                val turnOnTimeMillis = turnOnTimeMillisString?.toLongOrNull()

                if (turnOnTimeMillis != null) {
                    val currentTimestamp = System.currentTimeMillis()
                    val timeDifferenceMillis = currentTimestamp - turnOnTimeMillis

                    val hours = timeDifferenceMillis / (1000 * 60 * 60).toDouble()

                    val normalizedBri = (pastBri.toDouble() / 254.0) * 100
                    val estimatedPower = interpolate(normalizedBri.toInt())
                    val estimatedUsedPower = estimatedPower * hours

                    // 새로운 코드: usedPower 업데이트
                    val newUsedPower = currentDataSnapshot.child("usedPower").getValue(Double::class.java) ?: 0.0
                    val totalUsedPower = newUsedPower + estimatedUsedPower
                    lightReference.child("usedPower").setValue(totalUsedPower)
                } else {
                    println("turnOnTimeMillis를 Long으로 변환할 수 없습니다.")
                }
            } else if (isOn == false && currentIsOn == true) {
                // 조명이 꺼질 때, Firebase에 저장된 데이터 초기화
                lightReference.child("turnOntimeMillis").removeValue()
                lightReference.child("turnOntime").removeValue()
                lightReference.child("usedPower").removeValue()
            }
        }
        


        val url = "https://api.meethue.com/bridge/$hueUsername/lights/$lightId/state"
        val lightState = JSONObject()

        lightState.put("on", isOn)
        lightState.put("sat", 254)

        if (level > 0) {
            val brightness = level * 63 + 2
            lightState.put("bri", brightness)
        } else if (level == 0) {
            lightState.put("bri", 1)
        } else if (myBri != null) {
            lightState.put("bri", myBri)
        }

        val requestBodyString = lightState.toString()

        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Authorization", "Bearer $access_token")
            .header("Content-Type", "application/json")
            .build()

        val client = getUnsafeOkHttpClient()

        withContext(Dispatchers.Default) {
            try {
                val response: Response = client.newCall(request).execute()
                val responseBody = response.body
                if (response.isSuccessful && responseBody != null) {
                    withContext(Dispatchers.Main) {
                        if (isOn) {
                            binding.levelview.text = "조명이 켜졌습니다. 밝기: $level"
                        } else {
                            binding.levelview.text = "조명이 꺼졌습니다."
                        }
                    }
                    lightReference.child("bri").setValue(lightState.getInt("bri"))
                    lightReference.child("on").setValue(isOn)
                } else {
                    withContext(Dispatchers.Main) {
                        binding.levelview.text = "조명 상태 변경 실패: ${response.message}"
                    }
                }
            } catch (e: Exception) {
                println("Failed to set light state: $e")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MenuActivity, "조명 상태 변경 중 오류 발생", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // Trust all certificates
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun showSettingsPopup(anchorView: View) {
        // 팝업 표시
        popupManager.showPopup(anchorView, lightId)

        // Find the lights_delete_button inside the popupManager's popupView
        val lightsDeleteButton = popupManager.popupView?.findViewById<Button>(R.id.lights_delete_button)
        lightsDeleteButton?.setOnClickListener {
            popupManager.showDeleteConfirmationDialog()
        }
    }

}