package com.example.airquality

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray

class RedirectUriActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var accessToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        val code = uri?.getQueryParameter("code") ?: ""

        val clientId = BuildConfig.CLIENT_ID
        val clientSecret = BuildConfig.CLIENT_SECRET
        val authorizationHeader = Credentials.basic(clientId, clientSecret)

        val formBody = FormBody.Builder()
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build()

        val request = Request.Builder()
            .url("https://api.meethue.com/oauth2/token")
            .post(formBody)
            .header("Authorization", authorizationHeader)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log the error for debugging purposes
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody)
                    accessToken = json.getString("access_token") // Extract the token from the JSON response

                    // Save the token to Firebase
                    runOnUiThread {
                        saveTokenToFirebase(accessToken)
                    }

                    // Perform other actions
                    promptForLinkButton()
                } else {
                    // Log the error for debugging purposes
                    println("Error: ${response.code}")
                }
            }
        })
    }

    private fun saveTokenToFirebase(token: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid
        if (userId == null) {
            println("FirebaseAuth user is null")
            return
        }

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("users").child(userId)

        // Get the current time in milliseconds
        val currentTimeMillis = System.currentTimeMillis()

        myRef.child("accessToken").setValue(token)
        myRef.child("tokenCreatedTime").setValue(currentTimeMillis)
    }

    private fun promptForLinkButton() {
        enableLinkButton()
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Link Button")
                .setMessage("Please press the link button on your Hue Bridge.")
                .setPositiveButton("Confirm") { _, _ ->
                    enableLinkButton()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun enableLinkButton() {
        val request = Request.Builder()
            .url("https://api.meethue.com/bridge/0/config")
            .put("{\"linkbutton\":true}".toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    createUser()
                } else {
                    // Handle error
                }
            }
        })
    }

    private fun createUser() {
        val request = Request.Builder()
            .url("https://api.meethue.com/bridge/")
            .post("{\"devicetype\":\"myremotehueapp\"}".toRequestBody("application/json".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log the error for debugging purposes
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonArray = JSONArray(responseBody)
                    val jsonObject = jsonArray.getJSONObject(0)
                    val successObject = jsonObject.getJSONObject("success")
                    val username = successObject.getString("username")

                    // Save the user details to Firebase
                    runOnUiThread {
                        saveUserDetailsToFirebase(username, accessToken)
                        // 데이터 저장 및 처리가 완료되었으므로 다시 Lights_MenuActivity로 이동
                        navigateToLightsMenuActivity()
                    }
                } else {
                    // Log the error for debugging purposes
                    println("Error: ${response.code}")
                }
            }
        })
    }

    private fun navigateToLightsMenuActivity() {
        val intent = Intent(this, LightsMenuActivity::class.java)
        startActivity(intent)
        finish() // Optional: Close the current activity if needed
    }

    private fun saveUserDetailsToFirebase(username: String, token: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return // uid가 없다면 함수를 빠져나감

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("users").child(userId)

        // 현재 시간을 밀리초 단위로 얻습니다.
        val currentTimeMillis = System.currentTimeMillis()

        myRef.child("bridgeApiKey").setValue(username)
        myRef.child("accessToken").setValue(token)
        myRef.child("tokenCreatedTime").setValue(currentTimeMillis)
    }

}
