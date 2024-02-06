package com.example.airquality

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.example.airquality.data.Light
import com.example.airquality.data.TurnOffAlarm
import com.example.airquality.databinding.ActivityTurnOffAlarmBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TurnOffAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTurnOffAlarmBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTurnOffAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        auth = FirebaseAuth.getInstance()

        setButtonListeners()

        binding.AddAlarmButton.setOnClickListener {
            addAlarm()
        }

        loadAlarms()
    }

    private fun loadAlarms() {
        val userId = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users/$userId/TurnOffAlarms")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val alarmList = dataSnapshot.children.mapNotNull {
                    it.getValue(TurnOffAlarm::class.java)?.let { alarm ->
                        alarm.copy(AlarmId = it.key ?: "")  // copy를 사용하여 새 객체 생성
                    }
                }
                createSwitchesAndDeleteButtons(alarmList)
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle possible errors.
            }
        })
    }

    private fun createSwitchesAndDeleteButtons(alarmList: List<TurnOffAlarm>) {
        val alarmSwitchContainer = binding.alarmSwitchContainer

        // Clear existing views
        alarmSwitchContainer.removeAllViews()

        for (alarm in alarmList) {
            // Create the switch
            val switch = Switch(this).apply {
                text = "Alarm ${alarm.AlarmId}"
                textSize = 18f  // 큰 텍스트 크기
                isChecked = alarm.on
                thumbDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)
                trackDrawable.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
                setPadding(32, 32, 32, 32) // 패딩 증가
                setOnCheckedChangeListener { _, isChecked ->
                    val userId = auth.currentUser?.uid ?: return@setOnCheckedChangeListener
                    val alarmRef = FirebaseDatabase.getInstance().getReference("users/$userId/TurnOffAlarms/${alarm.AlarmId}")

                    val updateMap = HashMap<String, Any>()
                    updateMap["on"] = isChecked
                    alarmRef.updateChildren(updateMap).addOnSuccessListener {
                        // Successful update
                    }.addOnFailureListener {
                        // Failed update
                    }
                }
            }

            val alarmTimeTextView = TextView(this).apply {
                text = "${alarm.hour}:${alarm.minute}"  // 'hour'과 'minute'이 TurnOnAlarm 클래스 내의 알람 시간을 저장하는 필드라고 가정
                textSize = 18f
                setPadding(32, 32, 32, 32) // 패딩 증가
            }

            // Create the delete button
            val deleteButton = Button(this).apply {
                text = "Delete"
                textSize = 12f  // 텍스트 크기 줄임
                setBackgroundColor(Color.GRAY)
                setTextColor(Color.WHITE)
                setPadding(10, 10, 10, 10) // 패딩 줄임
                layoutParams = LinearLayout.LayoutParams(200, 100)
                setOnClickListener {
                    // AlertDialog for confirmation
                    AlertDialog.Builder(this@TurnOffAlarmActivity)
                        .setTitle("알람 삭제")
                        .setMessage("알람을 삭제하시겠습니까?")
                        .setPositiveButton("Ok") { dialog, which ->
                            val userId = auth.currentUser?.uid ?: return@setPositiveButton
                            val alarmRef = FirebaseDatabase.getInstance().getReference("users/$userId/TurnOnAlarms/${alarm.AlarmId}")
                            alarmRef.removeValue().addOnSuccessListener {
                                // Successful deletion
                                loadAlarms() // Refresh the UI
                            }.addOnFailureListener {
                                // Failed deletion
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            val editButton = Button(this).apply {
                text = "Edit"
                textSize = 12f  // 텍스트 크기 줄임
                setBackgroundColor(Color.GRAY)
                setTextColor(Color.WHITE)
                setPadding(10, 10, 10, 10) // 패딩 줄임
                layoutParams = LinearLayout.LayoutParams(200, 100)
                setOnClickListener {
                    // Open TimePicker dialog to edit the alarm time
                    showTimePicker(alarm.AlarmId, true)
                }
            }

            val space = Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    16,  // Width
                    LinearLayout.LayoutParams.MATCH_PARENT // Height
                )
            }

            // Create the layout to hold the switch, alarm time, delete button, and edit button
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(40, 40, 40, 40)
                setBackgroundColor(ContextCompat.getColor(this@TurnOffAlarmActivity, R.color.btngreen))  // 배경색 설정
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(20, 10, 20, 10)  // 마진 설정
                }

                addView(switch)
                addView(alarmTimeTextView) // Add the TextView here
                addView(deleteButton)
                addView(space)  // Add the space here
                addView(editButton)
            }

            // Add the layout to the parent container
            alarmSwitchContainer.addView(layout)
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

    private fun addAlarm() {
        val userId = auth.currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("users/$userId/TurnOffAlarms")

        // Fetch current number of alarms to set next AlarmId
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val currentCount = dataSnapshot.childrenCount
                val nextAlarmId = (currentCount + 1).toString()
                showTimeAlarmDialog(nextAlarmId)  // first call this with nextAlarmId
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Handle possible errors.
            }
        })
    }

    private fun showTimeAlarmDialog(nextAlarmId: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("시간 소등을 사용하시겠습니까?")

        builder.setPositiveButton("OK") { _, _ ->
            showTimePicker(nextAlarmId) // call this with nextAlarmId

        }

        builder.setNegativeButton("No") { _, _ ->
        }

        builder.show()
    }

    private fun showTimePicker(alarmId: String, isEditing: Boolean = false) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("어떤 조명을 끌까요?")
        val currentUser = auth.currentUser
        val userId = currentUser?.uid
        val ref = FirebaseDatabase.getInstance().getReference("users/$userId/lights")
        val timeRef = FirebaseDatabase.getInstance().getReference("users/$userId/TurnOffAlarms/$alarmId")

        val alarmData = HashMap<String, Any>()

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val lightDataList = dataSnapshot.children.mapNotNull {
                    it.getValue(Light::class.java)?.let { light ->
                        light.copy(lightId = it.key ?: "")  // copy를 사용하여 새 객체 생성
                    }
                }

                val switches = Array(lightDataList.size) { Switch(this@TurnOffAlarmActivity) }

                for ((index, lightData) in lightDataList.withIndex()) {
                    switches[index].text = "Light ${lightData.lightId}"  // lightId 사용
                }

                val switchContainer = LinearLayout(this@TurnOffAlarmActivity)
                switchContainer.orientation = LinearLayout.VERTICAL
                switches.forEach { switch ->
                    switchContainer.addView(switch)
                }

                builder.setView(switchContainer)

                builder.setPositiveButton("OK") { _, _ ->
                    val isTurnOff = mutableMapOf<String, Boolean>()

                    for ((index, lightData) in lightDataList.withIndex()) {
                        isTurnOff[lightData.lightId] = switches[index].isChecked
                    }

                    alarmData["isTurnOff"] = isTurnOff

                    val timePickerDialog = TimePickerDialog(
                        this@TurnOffAlarmActivity,
                        { _, hour, minute ->
                            alarmData["on"] = true
                            alarmData["hour"] = hour
                            alarmData["minute"] = minute
                            alarmData["AlarmId"] = alarmId

                            if (isEditing) {
                                timeRef.updateChildren(alarmData).addOnSuccessListener {
                                    // Update successful
                                    loadAlarms()
                                }.addOnFailureListener {
                                    // Update failed
                                }
                            } else {
                                timeRef.setValue(alarmData).addOnSuccessListener {
                                    // Save successful
                                    loadAlarms()
                                }.addOnFailureListener {
                                    // Save failed
                                }
                            }
                        },
                        12, 0, false
                    )

                    timePickerDialog.show()
                }

                builder.setNegativeButton("취소") { _, _ ->
                    // 취소를 클릭하면 다른 로직을 실행
                }

                builder.show()
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // 데이터를 가져오지 못했을 경우
            }
        })
    }
}