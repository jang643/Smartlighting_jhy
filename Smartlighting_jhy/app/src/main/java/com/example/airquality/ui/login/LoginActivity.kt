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
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
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
