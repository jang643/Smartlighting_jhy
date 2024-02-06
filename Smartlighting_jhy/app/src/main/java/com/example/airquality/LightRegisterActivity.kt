package com.example.airquality

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class LightRegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_light_register)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if the user is signed in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize Firebase Realtime Database
        val database = FirebaseDatabase.getInstance()
        val userId = currentUser.uid
        val userRef = database.getReference("users").child(userId)

        val editTextBridgeIp: EditText = findViewById(R.id.editTextBridgeIp)
        val buttonSaveIp: Button = findViewById(R.id.buttonSaveIp)

        // When the button is clicked, save the IP address to Firebase and register the user to the bridge.
        buttonSaveIp.setOnClickListener {
            val ip = editTextBridgeIp.text.toString()
            if (ip.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val savedIp = getSavedIpFromFirebase(userRef)
                    if (savedIp == ip) {
                        val apiKey = getSavedApiKeyFromFirebase(userRef)
                        if (apiKey != null) {
                            val lightCount = fetchLightCountFromBridge(ip, apiKey)
                            if (lightCount != null) {
                                saveLightCountToFirebase(userRef, lightCount)
                            }
                        }
                    } else {
                        val ipSaveSuccess = saveIpToFirebase(userRef, ip)
                        if (ipSaveSuccess) {
                            val apiKey = registerUserToBridge(ip)
                            if (apiKey != null) {
                                saveApiKeyToFirebase(userRef, apiKey)
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please Enter IP Address", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveIpToFirebase(userRef: DatabaseReference, ip: String): Boolean {
        return withContext(Dispatchers.IO) {
            var success = false
            userRef.child("bridgeIp").setValue(ip).addOnSuccessListener {
                success = true
            }.addOnFailureListener {
                success = false
            }.await()
            success
        }
    }

    private suspend fun registerUserToBridge(ip: String): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = "http://$ip/api"
            val json = JSONObject()
            json.put("devicetype", "UPSIL")
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string()
                val jsonResponseArray = JSONArray(responseData)
                val firstItem = jsonResponseArray.getJSONObject(0)

                if (firstItem.has("success")) {
                    val success = firstItem.getJSONObject("success")
                    return@withContext success.optString("username")
                } else {
                    val error = firstItem.optJSONObject("error")?.optString("description")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LightRegisterActivity, "오류: $error", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext null
                }
            }
        }
    }

    private suspend fun getSavedIpFromFirebase(userRef: DatabaseReference): String? {
        return withContext(Dispatchers.IO) {
            var savedIp: String? = null
            userRef.child("bridgeIp").get().addOnSuccessListener { snapshot ->
                savedIp = snapshot.getValue(String::class.java)
            }.await()
            savedIp
        }
    }

    private suspend fun getSavedApiKeyFromFirebase(userRef: DatabaseReference): String? {
        return withContext(Dispatchers.IO) {
            var savedApiKey: String? = null
            userRef.child("bridgeApiKey").get().addOnSuccessListener { snapshot ->
                savedApiKey = snapshot.getValue(String::class.java)
            }.await()
            savedApiKey
        }
    }

    private suspend fun fetchLightCountFromBridge(ip: String, apiKey: String): Int? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = "http://$ip/api/$apiKey/lights" // 예시 URL, 실제 URL은 브릿지 API에 따라 다를 것입니다.
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string()
                val jsonResponse = JSONObject(responseData)
                return@withContext jsonResponse.length() // 전구 개수를 예상하여 반환합니다.
            }
        }
    }

    private fun saveLightCountToFirebase(userRef: DatabaseReference, lightCount: Int) {
        userRef.child("lightCount").setValue(lightCount).addOnSuccessListener {
            Toast.makeText(this@LightRegisterActivity, "전구 개수 저장 성공", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this@LightRegisterActivity, "전구 개수 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveApiKeyToFirebase(userRef: DatabaseReference, apiKey: String) {
        userRef.child("bridgeApiKey").setValue(apiKey).addOnSuccessListener {
            Toast.makeText(this@LightRegisterActivity, "API 키 저장 성공", Toast.LENGTH_SHORT).show()
            val intent = Intent(this@LightRegisterActivity, LightsMenuActivity::class.java)
            startActivity(intent)
            finish()
        }.addOnFailureListener {
            Toast.makeText(this@LightRegisterActivity, "API 키 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }
}
