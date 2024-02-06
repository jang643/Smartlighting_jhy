package com.example.airquality.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.airquality.MainActivity
import com.example.airquality.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("users")

        binding.buttonRegister.setOnClickListener {
            val email = binding.editTextEmail.text.toString()
            val password = binding.editTextPassword.text.toString()
            val username = binding.editTextUsername.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && username.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        val userMap = hashMapOf(
                            "username" to username,
                            "email" to email
                        )

                        userRef.child(userId).setValue(userMap).addOnCompleteListener { task2 ->
                            if (task2.isSuccessful) {
                                // 회원가입 및 사용자 정보 저장 성공, MainActivity로 이동
                                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                                finish()
                            } else {
                                // 사용자 정보 저장 실패
                                Toast.makeText(baseContext, "사용자 정보 저장 실패: ${task2.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // 회원가입 실패
                        Toast.makeText(baseContext, "인증 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(baseContext, "이메일, 비밀번호, 사용자 이름을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
