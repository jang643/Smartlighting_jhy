package com.example.airquality

import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.airquality.data.Light
import com.example.airquality.databinding.ActivitySunLightBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SunLightActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySunLightBinding

    // Firebase 인증 객체 초기화
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySunLightBinding.inflate(layoutInflater)
        setContentView(binding.root)  // 이렇게 하면 괜찮습니다. 위에 있는 `setContentView(R.layout.activity_sun_light)`는 제거하세요.

        setButtonListeners()

        val sunRiseSwitch = findViewById<Switch>(R.id.sunriseswitch)
        val sunSetSwitch = findViewById<Switch>(R.id.sunsetswitch)

        // 스위치 상태가 변경될 때마다 호출되는 리스너 설정
        sunRiseSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSunriseAlarmDialog()
            } else {
                // 스위치가 꺼진 경우, Firebase에 저장되어 있는 값을 false로 변경
                val userId = auth.currentUser?.uid ?: return@setOnCheckedChangeListener
                val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunRise")
                ref.child("on").setValue(false)
            }
        }

        sunSetSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showSunsetAlarmDialog()
            } else {
                // 스위치가 꺼진 경우, Firebase에 저장되어 있는 값을 false로 변경
                val userId = auth.currentUser?.uid ?: return@setOnCheckedChangeListener
                val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunSet")
                ref.child("on").setValue(false)
            }
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.parent_layout)
        val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)

        rootLayout.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!isPointInsideView(event.rawX, event.rawY, relativeLayout)) {
                    relativeLayout.visibility = View.GONE
                }
            }
            false
        }
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
        val tabButton = binding.tabButton
        tabButton.setOnClickListener {
            val relativeLayout = findViewById<RelativeLayout>(R.id.relative_layout)
            relativeLayout.visibility = View.VISIBLE

            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.fragment_container, Tab1Fragment())
            fragmentTransaction.commit()
        }
    }

    private fun showSunriseAlarmDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("일출 시 기상 조명을 사용하시겠습니까?")

        builder.setPositiveButton("OK") { _, _ ->
            val userId = auth.currentUser?.uid ?: return@setPositiveButton
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunRise")
            ref.child("on").setValue(true)
            // 다음 단계: 사용자에게 어떤 조명을 켤지 물어봅니다.
            showsunriseLightSelectionDialog()
        }

        builder.setNegativeButton("No") { _, _ ->
            val userId = auth.currentUser?.uid ?: return@setNegativeButton
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunRise")
            ref.child("on").setValue(false)
        }

        builder.show()
    }

    // showLightSelectionDialog 메서드의 구현 (여기에 코드를 작성하십시오)
    private fun showsunriseLightSelectionDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("어떤 조명을 켤까요?")

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/lights")

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val lightDataList = dataSnapshot.children.mapNotNull {
                        it.getValue(Light::class.java)?.let { light ->
                            light.copy(lightId = it.key ?: "")  // copy를 사용하여 새 객체 생성
                        }
                    }

                    val switches = Array(lightDataList.size) { Switch(this@SunLightActivity) }

                    for ((index, lightData) in lightDataList.withIndex()) {
                        switches[index].text = "Light ${lightData.lightId}"  // lightId 사용
                    }

                    val switchContainer = LinearLayout(this@SunLightActivity)
                    switchContainer.orientation = LinearLayout.VERTICAL
                    switches.forEach { switch ->
                        switchContainer.addView(switch)
                    }

                    builder.setView(switchContainer)

                    builder.setPositiveButton("OK") { dialog, which ->
                        val isOnMap = mutableMapOf<String, Boolean>()

                        for ((index, lightData) in lightDataList.withIndex()) {
                            isOnMap[lightData.lightId] = switches[index].isChecked
                        }

                        // Firebase의 sunSet -> isOn 에 맵으로 저장
                        val sunSetRef = FirebaseDatabase.getInstance().getReference("users/$userId/sunRise")
                        sunSetRef.child("isOn").setValue(isOnMap)
                    }

                    builder.setNegativeButton("No") { dialog, which ->
                        // 취소 버튼을 누르면 'on'을 false로 설정
                        val sunSetRef = FirebaseDatabase.getInstance().getReference("users/$userId/sunRise")
                        sunSetRef.child("on").setValue(false)
                    }

                    builder.show()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // 데이터를 가져오지 못했을 경우
                }
            })
        }
    }

    private fun showSunsetAlarmDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("일몰 시 야간 조명을 사용하시겠습니까?")

        builder.setPositiveButton("OK") { _, _ ->
            val userId = auth.currentUser?.uid ?: return@setPositiveButton
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunSet")
            ref.child("on").setValue(true)
            // 다음 단계: 사용자에게 어떤 조명을 켤지 물어봅니다.
            showsunsetLightSelectionDialog()
        }

        builder.setNegativeButton("No") { _, _ ->
            val userId = auth.currentUser?.uid ?: return@setNegativeButton
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/sunSet")
            ref.child("on").setValue(false)
        }

        builder.show()
    }

    // showLightSelectionDialog 메서드의 구현 (여기에 코드를 작성하십시오)
    private fun showsunsetLightSelectionDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("어떤 조명을 켤까요?")

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            val ref = FirebaseDatabase.getInstance().getReference("users/$userId/lights")

            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val lightDataList = dataSnapshot.children.mapNotNull {
                        it.getValue(Light::class.java)?.let { light ->
                            light.copy(lightId = it.key ?: "")  // copy를 사용하여 새 객체 생성
                        }
                    }

                    val switches = Array(lightDataList.size) { Switch(this@SunLightActivity) }

                    for ((index, lightData) in lightDataList.withIndex()) {
                        switches[index].text = "Light ${lightData.lightId}"  // lightId 사용
                    }

                    val switchContainer = LinearLayout(this@SunLightActivity)
                    switchContainer.orientation = LinearLayout.VERTICAL
                    switches.forEach { switch ->
                        switchContainer.addView(switch)
                    }

                    builder.setView(switchContainer)

                    builder.setPositiveButton("OK") { dialog, which ->
                        val isOnMap = mutableMapOf<String, Boolean>()

                        for ((index, lightData) in lightDataList.withIndex()) {
                            isOnMap[lightData.lightId] = switches[index].isChecked
                        }

                        // Firebase의 sunSet -> isOn 에 맵으로 저장
                        val sunSetRef = FirebaseDatabase.getInstance().getReference("users/$userId/sunSet")
                        sunSetRef.child("isOn").setValue(isOnMap)
                    }

                    builder.setNegativeButton("Cancel") { dialog, which ->
                        // 취소 버튼을 누르면 'on'을 false로 설정
                        val sunSetRef = FirebaseDatabase.getInstance().getReference("users/$userId/sunSet")
                        sunSetRef.child("on").setValue(false)
                    }


                    builder.show()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // 데이터를 가져오지 못했을 경우
                }
            })
        }
    }







}
