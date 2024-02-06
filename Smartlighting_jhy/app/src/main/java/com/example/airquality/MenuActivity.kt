package com.example.airquality

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.example.airquality.databinding.ActivityMenuBinding
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
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


class MenuActivity : AppCompatActivity() {

    lateinit var hueBridgeIP: String
    lateinit var hueUsername: String
    private lateinit var database: DatabaseReference

    private lateinit var binding: ActivityMenuBinding
    private val notificationChannelId = "weather_notification_channel"
    private val notificationId = 100

    private val handler = Handler(Looper.getMainLooper())
    private val delay: Long = 5 * 60 * 1000 // 5 minutes in milliseconds
    private val weatherDataRunnable: Runnable = object : Runnable {
        override fun run() {
            fetchWeatherData()
            handler.postDelayed(this, delay)
        }
    }

    private lateinit var lightId: String
    private lateinit var popupManager: PopupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setButtonListeners()
        createNotificationChannel()
        fetchWeatherData()

        val httpClient = getUnsafeOkHttpClient()
        popupManager = PopupManager(this, hueBridgeIP, hueUsername, lightId, httpClient)

        database = FirebaseDatabase.getInstance().getReference()

        database.child("users").child("your_user_id_here").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                hueBridgeIP = dataSnapshot.child("BridgeIP").getValue(String::class.java) ?: ""
                hueUsername = dataSnapshot.child("bridgeApiKey").getValue(String::class.java) ?: ""

                // 변수가 초기화되면 이 후의 작업을 수행
                postInitialization()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle errors here
            }
        })



        val receivedLightId = intent.getIntExtra("LIGHT_ID", -1)
        lightId = if (receivedLightId != -1) receivedLightId.toString() else "1"
        binding.lightView.text = "Light No.$lightId"

        // Initialize popupManager here
        popupManager = PopupManager(this, hueBridgeIP, hueUsername, lightId, getUnsafeOkHttpClient())

        val rootLayout = binding.root
        val relativeLayout = binding.relativeLayout

        val settingsPopup = PopupWindow(this)
        val popupView = layoutInflater.inflate(R.layout.popup_setting, null)
        settingsPopup.contentView = popupView

        val settingButton = binding.settingButton
        settingButton.setOnClickListener {
            showSettingsPopup(it)
        }


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
    }

    private fun postInitialization() {
        // 이곳에서 popupManager를 초기화
        popupManager = PopupManager(this, hueBridgeIP, hueUsername, lightId, getUnsafeOkHttpClient())
        setButtonListeners()
        createNotificationChannel()
        handler.post(weatherDataRunnable)
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


        binding.turnOff.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                setLightState(false, 0)
            }
        }

    }

    override fun onResume() {
        super.onResume()
        handler.post(weatherDataRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(weatherDataRunnable)
    }

    private suspend fun setLightState(isOn: Boolean, level: Int) {
        // Hue bridge의 IP 주소와 사용자 이름, 그리고 조명의 ID를 사용하여 URL을 설정하세요.
        val url = "http://$hueBridgeIP/api/$hueUsername/lights/$lightId/state"
        val lightState = JSONObject()
        lightState.put("on", isOn)
        lightState.put("sat", 254)
        lightState.put("bri", level * 63 - 1)
        lightState.put("hue", 100)

        // JSON 객체를 문자열로 변환
        val requestBodyString = lightState.toString()

        // 요청 생성
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        val client = getUnsafeOkHttpClient()

        withContext(Dispatchers.Default) {
            try {
                val response: Response = client.newCall(request).execute()
                val responseBody = response.body
                if (response.isSuccessful && responseBody != null) {
                    // UI 스레드에서 상태 메시지를 표시합니다.
                    withContext(Dispatchers.Main) {
                        binding.levelview.text = "LEVEL: $level"
                    }
                } else {
                    // UI 스레드에서 오류 메시지를 표시합니다.
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

    private fun deleteLight() {
        val deleteUrl = "http://$hueBridgeIP/api/$hueUsername/lights/$lightId"

        val request = okhttp3.Request.Builder()
            .url(deleteUrl)
            .delete()
            .build()

        val client = OkHttpClient()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MenuActivity, "조명이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@MenuActivity, LightsMenuActivity::class.java) // 변경된 부분
                        startActivity(intent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MenuActivity, "조명 삭제 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MenuActivity, "조명 삭제 중 오류 발생", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Weather Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel(notificationChannelId, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }
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

    private fun fetchWeatherData() {
        val weatherApiUrl = "https://vpw.my.id/klaen/weatherapi.json"
        var returnDay: Int = 0
        val request = JsonArrayRequest(
            Request.Method.GET, weatherApiUrl, null,
            { response ->
                try {
                    var mostRecentEpoch = 0L
                    var mostRecentLocation: JSONObject? = null
                    var previousDay = 1
                    for (i in 0 until response.length()) {
                        try {
                            val weatherData = response.getJSONObject(i)
                            if (!weatherData.isNull("location")) {
                                val locationObject = weatherData.getJSONObject("location")
                                val currentObject = weatherData.getJSONObject("current")
                                val localtimeEpoch = locationObject.getLong("localtime_epoch")

                                if (localtimeEpoch > mostRecentEpoch) {
                                    mostRecentEpoch = localtimeEpoch
                                    mostRecentLocation = locationObject
                                    val isDay = currentObject.getInt("is_day")
                                    returnDay = isDay
                                }
                            }
                            if (i == response.length() - 2) {
                                val previousData = response.getJSONObject(i)
                                previousDay = previousData.getJSONObject("current").getInt("is_day")
                            }
                        } catch (e: JSONException) {
                            // JSON 객체로 변환할 수 없는 경우, 해당 요소는 무시하고 다음 요소로 넘어갑니다.
                            e.printStackTrace()
                        }
                    }

                    if (returnDay != previousDay) {
                        val message = if (previousDay == 1) "해가 졌습니다!" else "해가 떴습니다!"
                        sendNotification(message)
                    }

                    mostRecentLocation?.let {
                        val name = it.optString("name", "N/A")
                        val localtime = it.optString("localtime", "N/A")
                        Log.d("Weather Data", "Location: $name, Local Time: $localtime, isDay: $returnDay")
                        binding.locationTextView.text = "Location: $name"
                        binding.localTimeTextView.text = "Local Time: $localtime"
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this@MenuActivity, "JSON 데이터 파싱 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                // 오류 처리
                error.printStackTrace()
                Toast.makeText(this@MenuActivity, "서버 요청 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        )
        // 요청을 큐에 추가합니다.
        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(request)
    }

    private fun sendNotification(message: String) {
        val intent = Intent(this, MenuActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("날씨 알림")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showSettingsPopup(anchorView: View) {
        // 여기에서 PopupManager를 사용하여 팝업을 표시
        popupManager.showPopup(anchorView)

        // Find the lights_delete_button inside the popupManager's popupView
        val lightsDeleteButton = popupManager.popupView?.findViewById<Button>(R.id.lights_delete_button)
        lightsDeleteButton?.setOnClickListener {
            deleteLight()
        }
    }
}
