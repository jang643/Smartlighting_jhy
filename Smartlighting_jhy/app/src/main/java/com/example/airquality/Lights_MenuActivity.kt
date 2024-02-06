package com.example.airquality

import android.content.Intent
import android.os.Bundle
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class LightsMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLightsMenuBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var usernameView: TextView
    private lateinit var bridgeIPView: TextView
    private var bridgeIP: String = ""
    private var apiKey: String = ""


    val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightsMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setButtonListeners()
        LightRepository.initialize(this)

        // Initialize Firebase Realtime Database
        val database = FirebaseDatabase.getInstance()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        usernameView = findViewById(R.id.usernameView)
        bridgeIPView = findViewById(R.id.BridgeIPView)

        val userId = auth.currentUser?.uid
        if (userId != null) {
            fetchUsernameAndBridgeIP(userId, database)
            fetchLightsAndCreateButtons(userId, database)
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.parent_layout)
        val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)

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

    private fun fetchLightsFromHueAPI(bridgeIP: String, apiKey: String) {
        val url = "http://$bridgeIP/api/$apiKey/lights"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 에러 처리
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        // 실패 처리
                        return
                    }

                    val jsonResponse = JSONObject(it.body!!.string())
                    val lightIds = jsonResponse.names()
                    val layout = findViewById<LinearLayout>(R.id.linear_layout_for_lights) // 이곳에 버튼 추가

                    runOnUiThread {
                        // UI 스레드에서 작업해야 함
                        for (i in 0 until (lightIds?.length() ?: 0)) {
                            val lightId = lightIds?.getString(i)
                            val lightButton = Button(this@LightsMenuActivity)
                            lightButton.text = "Light $lightId"
                            lightButton.tag = lightId
                            lightButton.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lightButton.setOnClickListener {
                                val intent = Intent(this@LightsMenuActivity, MenuActivity::class.java)
                                intent.putExtra("Light_Id", it.tag.toString())
                                startActivity(intent)
                            }
                            layout.addView(lightButton)
                        }
                    }
                }
            }
        })
    }

    private fun fetchLightsAndCreateButtons(userId: String, database: FirebaseDatabase) {
        val userRef = database.getReference("users").child(userId)
        userRef.child("lights").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val lightCount = dataSnapshot.child("count").getValue(Int::class.java) ?: 0
                val lightIds = dataSnapshot.child("ids").getValue(object : GenericTypeIndicator<ArrayList<String>>() {}) ?: arrayListOf()

                val layout = findViewById<LinearLayout>(R.id.linear_layout_for_lights) // 새로운 LinearLayout 아이디

                for (i in 0 until lightCount) {
                    val lightId = lightIds[i]
                    val lightButton = Button(this@LightsMenuActivity)
                    lightButton.text = "Light $lightId"
                    lightButton.tag = lightId
                    lightButton.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lightButton.setOnClickListener {
                        val intent = Intent(this@LightsMenuActivity, MenuActivity::class.java)
                        intent.putExtra("Light_Id", it.tag.toString())
                        startActivity(intent)
                    }
                    layout.addView(lightButton)
                }
                fetchLightsFromHueAPI(bridgeIP, apiKey)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle errors here
            }
        })
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

        binding.bridgeRegisterButton.setOnClickListener {
            val intent = Intent(this@LightsMenuActivity, LightRegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun fetchUsernameAndBridgeIP(userId: String, database: FirebaseDatabase) {
        val userRef = database.getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val username = dataSnapshot.child("username").getValue(String::class.java) ?: ""
                val bridgeIP = dataSnapshot.child("bridgeIp").getValue(String::class.java) ?: ""
                val apiKey = dataSnapshot.child("bridgeApiKey").getValue(String::class.java) ?: ""

                usernameView.text = "Hello, $username!"
                bridgeIPView.text = if (bridgeIP.isNotEmpty()) "Bridge IP: $bridgeIP" else "Bridge IP not set"

                findViewById<TextView>(R.id.APIkeyView).text = if (apiKey.isNotEmpty()) "API Key: $apiKey" else "API Key not set"
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle errors here
            }
        })
    }
}
