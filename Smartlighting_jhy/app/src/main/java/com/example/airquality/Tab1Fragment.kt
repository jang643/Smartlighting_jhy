package com.example.airquality

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.room.Room
import com.example.airquality.ui.login.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class Tab1Fragment : Fragment() {

    private lateinit var lightDao: LightDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 데이터베이스 인스턴스 생성
        val db: AppDatabase = Room.databaseBuilder(
            requireContext(),
            AppDatabase::class.java,
            "light-database"
        ).build()

        lightDao = db.lightDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tab1, container, false)

        val lightsButton: Button = view.findViewById(R.id.lightsButton)
        val logOutButton: Button = view.findViewById(R.id.LogOutButton) // 로그아웃 버튼 참조 추가

        // lightsButton을 클릭하면 LightsMenu 액티비티로 넘어갑니다.
        lightsButton.setOnClickListener {
            val intent = Intent(activity, LightsMenuActivity::class.java)
            startActivity(intent)
        }

        // logOutButton을 클릭하면 로그아웃 수행
        logOutButton.setOnClickListener {
            // Firebase 로그아웃 수행 (Firebase Authentication을 사용하는 경우)
            FirebaseAuth.getInstance().signOut()

            // 로그인 액티비티로 이동
            val intent = Intent(activity, LoginActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }

        return view
    }
}
