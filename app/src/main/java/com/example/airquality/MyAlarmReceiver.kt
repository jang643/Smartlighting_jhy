package com.example.airquality

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.airquality.data.SunInfoUtility
import com.example.airquality.data.TurnOffAlarm
import com.example.airquality.data.TurnOnAlarm
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MyAlarmReceiver : BroadcastReceiver() {
    private var hueUsername: String? = null
    private var access_token: String? = null

    override fun onReceive(context: Context, intent: Intent) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        val database = FirebaseDatabase.getInstance()
        val databaseRef = database.getReference("users").child(userId)

        // Get user information from the database
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                hueUsername = dataSnapshot.child("bridgeApiKey").getValue(String::class.java)
                access_token = dataSnapshot.child("accessToken").getValue(String::class.java)

                if (hueUsername == null || access_token == null) {
                    println("Failed to load hueUsername or access_token from Firebase.")
                    return
                }

                when (intent.action) {
                    "com.example.airquality.ACTION_TURN_ON" -> {
                        handleTurnOnAlarm(databaseRef, intent)
                    }
                    "com.example.airquality.ACTION_TURN_OFF" -> {
                        handleTurnOffAlarm(databaseRef, intent)
                    }
                    "com.example.airquality.ACTION_FETCH_SUNRISE_SUNSET" -> {
                        SunInfoUtility.updateSunriseSunset(context, databaseRef)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                println("Error fetching data from Firebase: $databaseError")
            }
        })
    }

    private fun handleTurnOnAlarm(databaseRef: DatabaseReference, intent: Intent) {
        val turnOnAlarm = databaseRef.child("TurnOnAlarms").child(intent.getStringExtra("AlarmId") ?: "")
        turnOnAlarm.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarm = snapshot.getValue(TurnOnAlarm::class.java) ?: return

                if (alarm.on && hueUsername != null && access_token != null) {
                    alarm.isTurnOn.forEach { (lightId, isOn) ->
                        if (isOn) {
                            TurnOnLight(lightId)
                        }
                    }
                } else {
                    // 로그 또는 에러 처리
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle errors
            }
        })
    }


    private fun handleTurnOffAlarm(databaseRef: DatabaseReference, intent: Intent) {
        val turnOffAlarm = databaseRef.child("TurnOffAlarms").child(intent.getStringExtra("AlarmId") ?: "")
        turnOffAlarm.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alarm = snapshot.getValue(TurnOffAlarm::class.java) ?: return
                if (alarm.on) {
                    alarm.isTurnOff.forEach { (lightId, isOff) ->
                        if (isOff) {
                            TurnOffLight(lightId)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle errors
            }
        })
    }

    private fun TurnOnLight(lightId: String) {
        val url = "https://api.meethue.com/bridge/$hueUsername/lights/$lightId/state"
        val lightState = JSONObject()
        lightState.put("on", true)
        changeLightState(url, lightState)
    }

    private fun TurnOffLight(lightId: String) {
        val url = "https://api.meethue.com/bridge/$hueUsername/lights/$lightId/state"
        val lightState = JSONObject()
        lightState.put("on", false)
        changeLightState(url, lightState)
    }

    private fun changeLightState(url: String, lightState: JSONObject) {
        val requestBodyString = lightState.toString()

        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Authorization", "Bearer $access_token")
            .header("Content-Type", "application/json")
            .build()

        val client = getUnsafeOkHttpClient()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    println("Successfully changed light state: ${response.body?.string()}")
                } else {
                    println("Failed to change light state: ${response.code}, ${response.message}")
                }
            } catch (e: Exception) {
                println("Exception when trying to change light state: $e")
            }
        }
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
