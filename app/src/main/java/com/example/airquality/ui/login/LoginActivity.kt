package com.example.airquality.ui.login

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.airquality.MainActivity
import com.example.airquality.R
import com.example.airquality.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import okhttp3.Credentials
import okhttp3.OkHttpClient



class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!isConnected()) {
            Toast.makeText(this, "No network connection", Toast.LENGTH_SHORT).show()
            return
        }

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())
            .get(LoginViewModel::class.java)

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer { loginState ->
            val login = binding.login
            login.isEnabled = loginState?.isDataValid ?: false

            loginState?.usernameError?.let {
                binding.username.error = getString(it)
            }

            loginState?.passwordError?.let {
                binding.password.error = getString(it)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer { loginResult ->
            binding.loading.visibility = View.GONE
            loginResult?.error?.let {
                showLoginFailed(it)
            }
            loginResult?.success?.let {
                updateUiWithUser(it)
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        })

        setUpListeners()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // 파이어베이스에서 토큰의 생성 시간을 가져옵니다 (이 부분을 알맞게 구현해 주세요)
            val tokenCreationTime = getTokenCreationTimeFromFirebase()

            // 현재 시간을 가져옵니다.
            val currentTime = System.currentTimeMillis()

            // 토큰의 유효기간이 7일(604800000 밀리초)라고 가정합니다.
            if (currentTime - tokenCreationTime >= 604800000 - 86400000) { // 86400000 밀리초는 하루
                // 토큰을 새로 발급받습니다.
                refreshAccessToken(getRefreshTokenFromFirebase())
            }

            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun refreshAccessToken(refreshToken: String) {
        val clientId = "YOUR_CLIENT_ID"
        val clientSecret = "YOUR_CLIENT_SECRET"
        val authorizationHeader = Credentials.basic(clientId, clientSecret)
        val client = OkHttpClient()


        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("https://api.meethue.com/oauth2/refresh?grant_type=refresh_token")
            .post(formBody)
            .header("Authorization", authorizationHeader)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle the error
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    // Extract and update the access token from responseBody
                    // 예: accessToken = "NEW_EXTRACTED_ACCESS_TOKEN"
                } else {
                    // Handle the error
                }
            }
        })
    }

    fun getTokenCreationTimeFromFirebase(): Long {
        // 초기값으로 현재 시간을 반환합니다.
        var tokenCreationTime = System.currentTimeMillis()

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("userTokens").child(auth.currentUser!!.uid)

        myRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                tokenCreationTime = dataSnapshot.child("tokenCreationTime").getValue(Long::class.java) ?: System.currentTimeMillis()
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
            }
        })
        return tokenCreationTime
    }

    fun getRefreshTokenFromFirebase(): String {
        var refreshToken = ""

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("userTokens").child(auth.currentUser!!.uid)

        myRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                refreshToken = dataSnapshot.child("refreshToken").getValue(String::class.java) ?: ""
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
            }
        })
        return refreshToken
    }


    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()  // 앱의 모든 액티비티를 종료
    }

    private fun isConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun setUpListeners() {
        val username = binding.username
        val password = binding.password

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    loginViewModel.login(username.text.toString(), password.text.toString())
                }
                false
            }

            binding.login.setOnClickListener {
                val usernameStr = username.text.toString()
                val passwordStr = password.text.toString()

                if (usernameStr.isEmpty() || passwordStr.isEmpty()) {
                    Toast.makeText(baseContext, "Username or Password should not be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                binding.loading.visibility = View.VISIBLE
                auth.signInWithEmailAndPassword(usernameStr, passwordStr)
                    .addOnCompleteListener(this@LoginActivity) { task ->
                        binding.loading.visibility = View.GONE
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            updateUiWithUser(LoggedInUserView(displayName = user?.displayName ?: ""))
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            }




        }
        // Inside setUpListeners() function
        binding.register?.setOnClickListener {
            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
            Log.d("LoginActivity", "Register button clicked")
        }

    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        Toast.makeText(
            applicationContext,
            getString(R.string.welcome, model.displayName),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}