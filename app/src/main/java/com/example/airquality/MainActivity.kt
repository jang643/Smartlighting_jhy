package com.example.airquality

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.airquality.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.airquality.data.SunInfoUtility
import com.example.airquality.data.SunriseSunsetResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import kotlinx.coroutines.DelicateCoroutinesApi
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val database = FirebaseDatabase.getInstance().getReference("users")
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val WEATHER_API_KEY = "6b91bdf44ce5c196f5ac7948accfb4eb"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setButtonListeners()
        setRootLayoutTouchListener()
        checkLocationPermission()
        setDailyUpdateAlarm()
        scheduleWork()
        fetchAndSetFirebaseAlarms()
    }

    private fun setButtonListeners() {
        binding.tabButton.setOnClickListener {
            binding.relativeLayout.visibility = View.VISIBLE
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, Tab1Fragment()).commit()
        }
    }

    private fun setRootLayoutTouchListener() {
        binding.rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isPointInsideView(event.rawX, event.rawY, binding.relativeLayout)) {
                        binding.relativeLayout.visibility = View.GONE
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2).apply { view.getLocationOnScreen(this) }
        return x in location[0].toFloat()..(location[0] + view.width).toFloat() &&
                y in location[1].toFloat()..(location[1] + view.height).toFloat()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            getLocation()
        }
    }

    data class WeatherResponse(
        val weather: List<WeatherInfo>,
        val main: MainInfo,
        val clouds: CloudInfo
    )
    data class CloudInfo(
        val cloud: Int,
    )

    data class WeatherInfo(
        val description: String,
    )

    data class MainInfo(
        val temp: Double,
        val humidity: Int
    )



    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            val apiUrl = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$WEATHER_API_KEY"
            try {
                val result = fetchWeatherInfo(apiUrl)
                val weatherResponse = Gson().fromJson(result, WeatherResponse::class.java)
                Log.d("DEBUG", " $weatherResponse")
                withContext(Dispatchers.Main) {
                    // weatherResponse를 사용하여 원하는 날씨 정보를 추출합니다.
                    val temperature = String.format("%.1f", weatherResponse.main.temp - 273)// 예시
                    val cloud = weatherResponse.clouds.cloud
                    val humidity = weatherResponse.main.humidity
                    val weatherDescription = weatherResponse.weather[0].description // 예시

                    // CardView 또는 View 생성
                    val cardView = CardView(this@MainActivity)
                    cardView.setBackgroundResource(R.color.btngreen)  // 적절한 색상 리소스를 사용하세요.
                    cardView.radius = 16f  // 코너 둥글게
                    cardView.cardElevation = 8f  // 그림자 추가
                    cardView.setContentPadding(16, 16, 16, 16)
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        convertDpToPixel(200, this@MainActivity) // 200dp를 픽셀 값으로 변환
                    )
                    layoutParams.setMargins(16, 16, 16, 16) // 마진 설정
                    cardView.layoutParams = layoutParams


                    // CardView에 표시할 TextView 생성 및 설정
                    val textView = TextView(this@MainActivity)
                    textView.text = "Temperature: $temperature \nDescription: $weatherDescription\nCloud: $cloud \nHumidity: $humidity"
                    cardView.addView(textView)

                    // LinearLayout에 CardView 추가
                    val linearLayout = findViewById<LinearLayout>(R.id.linear_layout_for_lights)
                    linearLayout.addView(cardView)
                }

            } catch (e: Exception) {
                notifyFailure("Error occurred: ${e.message}")
            }
        }
    }

    fun convertDpToPixel(dp: Int, context: Context): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }


    private fun fetchWeatherInfo(apiUrl: String): String {
        val url = URL(apiUrl)
        val urlConnection = url.openConnection() as HttpURLConnection
        return try {
            urlConnection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            urlConnection.disconnect()
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                fetchWeatherData(it.latitude, it.longitude)  // 위치를 가져오면 날씨 데이터도 가져옵니다.
                fetchAndDisplaySunriseSunset(it.latitude, it.longitude)
            } ?: Log.d("DEBUG", "Location is null")
        }.addOnFailureListener { exception ->
            Log.d("DEBUG", "Error getting location: ${exception.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("DEBUG", "Location permission granted")
                getLocation()
            } else {
                Log.d("DEBUG", "Location permission denied")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchAndDisplaySunriseSunset(latitude: Double, longitude: Double) {
        GlobalScope.launch(Dispatchers.IO) {
            val apiUrl = "https://api.sunrisesunset.io/json?lat=$latitude&lng=$longitude"
            try {
                val result = fetchSunriseSunsetInfo(apiUrl)
                val response = Gson().fromJson(result, SunriseSunsetResponse::class.java)
                if (response.status == "OK") {
                    val sunriseTime = response.results.sunrise
                    val sunsetTime = response.results.sunset

                    // 여기서 sunriseTime과 sunsetTime을 UI에 표시하는 코드를 추가
                    withContext(Dispatchers.Main) {
                        binding.sunriseTimeTextView.text = "Sunrise: $sunriseTime"
                        binding.sunsetTimeTextView.text = "Sunset: $sunsetTime"
                    }

                    // 알람 설정
                    SunInfoUtility.setAlarm(this@MainActivity, sunriseTime, "SUNRISE")
                    SunInfoUtility.setAlarm(this@MainActivity, sunsetTime, "SUNSET")
                } else {
                    notifyFailure("Failed to fetch sunrise/sunset data")
                }
            } catch (e: Exception) {
                notifyFailure("Error occurred: ${e.message}")
            }
        }
    }


    private suspend fun notifyFailure(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchSunriseSunsetInfo(apiUrl: String): String {
        val url = URL(apiUrl)
        val urlConnection = url.openConnection() as HttpURLConnection
        return try {
            urlConnection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            urlConnection.disconnect()
        }
    }

    private fun setAlarm(time: String, type: String) {
        val format = SimpleDateFormat("hh:mm a", Locale.US)
        val date = format.parse(time) ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, date.hours)
            set(Calendar.MINUTE, date.minutes)
        }
        val intentAction = if (type == "SUNRISE") "com.example.airquality.ACTION_SUNRISE" else "com.example.airquality.ACTION_SUNSET"
        val intent = Intent(this, MyAlarmReceiver::class.java).apply {
            action = intentAction
            putExtra("AlarmId", type)
        }
        val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // API 31 이상에서만 canScheduleExactAlarms() 메서드를 호출
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmMgr.canScheduleExactAlarms()) {
                try {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
                } catch (e: SecurityException) {
                    // Handle the SecurityException. Maybe show a message to the user about needing the permission.
                    Toast.makeText(this, "Permission needed to set exact alarms.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Maybe show a message or some UI to guide the user to grant the permission from system settings.
                Toast.makeText(this, "Please allow the app to schedule exact alarms from system settings.", Toast.LENGTH_LONG).show()
            }
        } else {
            // API 31 미만에서는 이전 방식으로 알람을 설정
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
        }
    }

    private fun setDailyUpdateAlarm() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 2)
        }
        val intent = Intent(this, MyAlarmReceiver::class.java)
        val alarmIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, alarmIntent)
    }

    private fun scheduleWork() {
        val workRequest = PeriodicWorkRequestBuilder<MyBackgroundWorker>(1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun fetchAndSetFirebaseAlarms() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid // 사용자의 UID를 가져옴


        if (userId != null) {
            val userRef = database.child(userId)
            userRef.child("sunRise").get().addOnSuccessListener { snapshot ->
                val isOn = snapshot.child("on").getValue(Boolean::class.java) ?: false
                if (isOn) {
                    val time =
                        snapshot.child("sunInfo").child("sunrise").getValue(String::class.java)
                            ?: return@addOnSuccessListener
                    setAlarm(time, "SUNRISE")
                }
            }

            // sunSet 알람 설정
            userRef.child("sunSet").get().addOnSuccessListener { snapshot ->
                val isOn = snapshot.child("on").getValue(Boolean::class.java) ?: false
                if (isOn) {
                    val time =
                        snapshot.child("sunInfo").child("sunset").getValue(String::class.java)
                            ?: return@addOnSuccessListener
                    setAlarm(time, "SUNSET")
                }
            }

            // TurnOffAlarms 알람 설정
            userRef.child("TurnOffAlarms").get().addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    val isOn = child.child("on").getValue(Boolean::class.java) ?: continue
                    if (isOn) {
                        val hour = child.child("hour").getValue(Int::class.java) ?: continue
                        val minute = child.child("minute").getValue(Int::class.java) ?: continue
                        setCustomAlarm(hour, minute, "TURNOFF")
                    }
                }
            }

            userRef.child("TurnOnAlarms").get().addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    val isOn = child.child("on").getValue(Boolean::class.java) ?: continue
                    if (isOn) {
                        val hour = child.child("hour").getValue(Int::class.java) ?: continue
                        val minute = child.child("minute").getValue(Int::class.java) ?: continue
                        setCustomAlarm(hour, minute, "TURNON")
                    }
                }
            }
        }
    }

    private fun setCustomAlarm(hour: Int, minute: Int, type: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val intentAction = "com.example.airquality.ACTION_$type"
        val intent = Intent(this, MyAlarmReceiver::class.java).apply {
            action = intentAction
            putExtra("AlarmId", type)
        }
        val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, alarmIntent)
    }

}
