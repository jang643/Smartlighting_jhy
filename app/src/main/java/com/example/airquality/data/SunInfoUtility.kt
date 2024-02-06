package com.example.airquality.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.airquality.MyAlarmReceiver
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SunInfoUtility {

    fun updateSunriseSunset(context: Context, databaseRef: DatabaseReference) {
        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 여기서 필요하다면 사용자에게 위치 권한을 요청하는 코드를 추가할 수 있습니다.
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val latitude = it.latitude
                val longitude = it.longitude

                GlobalScope.launch(Dispatchers.IO) {
                    val apiUrl = "https://api.sunrisesunset.io/json?lat=$latitude&lng=$longitude"
                    val result = fetchSunriseSunsetInfo(apiUrl)

                    val gson = Gson()
                    val response = gson.fromJson(result, SunriseSunsetResponse::class.java)

                    val sunriseTime = response.results.sunrise
                    val sunsetTime = response.results.sunset

                    // 파이어베이스에 저장
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    databaseRef.child(userId).child("sunInfo").child("sunrise").setValue(sunriseTime)
                    databaseRef.child(userId).child("sunInfo").child("sunset").setValue(sunsetTime)

                    // AlarmManager 설정
                    setAlarm(context, sunriseTime, "SUNRISE")
                    setAlarm(context, sunsetTime, "SUNSET")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Sunrise: $sunriseTime, Sunset: $sunsetTime", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun setAlarm(context: Context, time: String, type: String) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmMgr.canScheduleExactAlarms()) {
                Toast.makeText(context, "App doesn't have permission to schedule exact alarms", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val intent = Intent(context, MyAlarmReceiver::class.java).apply {
            action = if (type == "SUNRISE") "com.example.airquality.ACTION_TURN_ON" else "com.example.airquality.ACTION_TURN_OFF"
            putExtra("AlarmId", "sunrise_or_sunset_alarm")
        }
        val alarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.US) // 변경된 로케일 설정
        val date = sdf.parse(time)
        val calendar = Calendar.getInstance()

        if (date != null) {
            calendar.time = date

            alarmMgr.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                alarmIntent
            )
        }
    }


    private fun fetchSunriseSunsetInfo(apiUrl: String): String {
        val url = URL(apiUrl)
        val urlConnection = url.openConnection() as HttpURLConnection

        return try {
            val inputStream = urlConnection.inputStream
            inputStream.bufferedReader().use { it.readText() }
        } finally {
            urlConnection.disconnect()
        }
    }
}
