package com.example.airquality

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.airquality.databinding.ActivityAlarmBinding
import com.google.firebase.auth.FirebaseAuth

class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setButtonListeners()

        binding.SunriseAlarmButton.setOnClickListener {
            val intent = Intent(this, SunLightActivity::class.java)
            startActivity(intent)
        }

        binding.TurnOnButton.setOnClickListener {
            val intent = Intent(this, TurnOnAlarmActivity::class.java)
            startActivity(intent)
        }

        binding.TurnOffButton.setOnClickListener {
            val intent = Intent(this, TurnOffAlarmActivity::class.java)
            startActivity(intent)
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
}
