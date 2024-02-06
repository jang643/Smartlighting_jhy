package com.example.airquality

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class PopupManager(private val context: Context, private val hueBridgeIP: String, private val hueUsername: String, private val lightId: String, private val client: OkHttpClient) {
    var popupView: View? = null // Initialize popupView as a nullable variable

    fun showPopup(anchorView: View) {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        popupView = inflater.inflate(R.layout.popup_setting, null) // Initialize popupView with inflated layout

        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.showAsDropDown(anchorView)

        // Find the lights_delete_button inside the popupView
        val lightsDeleteButton = popupView?.findViewById<Button>(R.id.lights_delete_button)
        lightsDeleteButton?.setOnClickListener {
            deleteLight()
        }
    }

    private fun deleteLight() {
        val deleteUrl = "http://$hueBridgeIP/api/$hueUsername/lights/$lightId"
        val request = okhttp3.Request.Builder()
            .url(deleteUrl)
            .delete()
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "조명이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(context, LightsMenuActivity::class.java)
                        context.startActivity(intent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "조명 삭제 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "조명 삭제 중 오류 발생", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}