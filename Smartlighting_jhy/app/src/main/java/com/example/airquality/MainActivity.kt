package com.example.airquality

import com.example.airquality.Tab1Fragment
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import com.example.airquality.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationChannelId = "weather_notification_channel"
    private val notificationId = 100

    private val handler = Handler(Looper.getMainLooper())
    private val delay: Long = 5 * 60 * 1000 // 5분 (밀리초 단위)
    val fragment = Tab1Fragment()
    private val weatherDataRunnable: Runnable = object : Runnable {
        override fun run() {
            //fetchWeatherData()
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setButtonListeners()
        createNotificationChannel()
        //fetchWeatherData()

        val rootLayout = findViewById<ConstraintLayout>(R.id.root_layout)
        val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout) // XML에 이미 이 RelativeLayout에 대한 ID가 없다면 추가해 주세요.


        rootLayout?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!isPointInsideView(event.rawX, event.rawY, relativeLayout)) {
                    relativeLayout?.visibility = View.GONE
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
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
        binding.tabButton.setOnClickListener {
            val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)
            relativeLayout.visibility = View.VISIBLE

            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, Tab1Fragment())
            fragmentTransaction.commit()
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


    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Weather Notification Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(notificationChannelId, channelName, importance)
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


    /*private fun fetchWeatherData() {
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
                            if (i == response.length()-2) {
                                val previousData = response.getJSONObject(i)
                                previousDay = previousData.getJSONObject("current").getInt("is_day")
                            }
                        } catch (e: JSONException) {
                            // JSON 객체로 변환할 수 없는 경우, 해당 요소는 무시하고 다음 요소로 넘어갑니다.
                            e.printStackTrace()
                        }
                    }

                    if(returnDay != previousDay){
                        if(previousDay == 1){
                            sendNotification("해가 졌습니다!")
                        }
                        else{
                            sendNotification("해가 떴습니다!")
                        }
                    }

                    mostRecentLocation?.let {
                        val name = it.optString("name", "N/A")
                        val localtime = it.optString("localtime", "N/A")
                        Log.d("Weather Data", "Location: $name, Local Time: $localtime, isDay: $returnDay")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "JSON 데이터 파싱 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                // 오류 처리
                error.printStackTrace()
                Toast.makeText(this@MainActivity, "서버 요청 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        )
        // 요청을 큐에 추가합니다.
        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(request)
    }*/

    private fun sendNotification(message: String) {
        val intent = Intent(this, MainActivity::class.java)
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

}
