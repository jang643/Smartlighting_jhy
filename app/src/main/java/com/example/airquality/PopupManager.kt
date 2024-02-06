package com.example.airquality

import android.content.Context
import android.graphics.Color
import android.service.autofill.UserData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.example.airquality.data.Light
import com.example.airquality.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject


class PopupManager(private val context: Context, private val client: OkHttpClient, private val lightId: String) {
    private var hueUsername: String = ""
    private var access_token: String = ""

    var popupView: View? = null

    fun showPopup(anchorView: View, lightId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // Firebase에서 데이터를 가져오고 설정
            fetchUserDataFromFirebase()

            withContext(Dispatchers.Main) {
                // Popup UI 작성
                val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                popupView = inflater.inflate(R.layout.popup_setting, null)

                val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
                popupWindow.showAsDropDown(anchorView)

                // Button event listeners
                val lightsDeleteButton = popupView?.findViewById<Button>(R.id.lights_delete_button)
                lightsDeleteButton?.setOnClickListener {
                    showDeleteConfirmationDialog()
                }

                val brightnessSettingButton = popupView?.findViewById<Button>(R.id.brightness_setting_button)
                brightnessSettingButton?.setOnClickListener {
                    showBrightnessSettingDialog()
                }

                val colorSettingButton = popupView?.findViewById<Button>(R.id.color_setting_button)
                colorSettingButton?.setOnClickListener {
                    showColorSettingDialog()
                }
            }
        }
    }

    private suspend fun fetchUserDataFromFirebase() {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return
        val database = FirebaseDatabase.getInstance().getReference("users/$userId")

        try {
            val snapshot = database.get().await()
            if (snapshot.exists()) {
                val userData = snapshot.getValue(User::class.java)
                if (userData != null) {
                    hueUsername = userData.bridgeApiKey.toString()
                    access_token = userData.accessToken.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 에러 처리
        }
    }

    private fun showColorSettingDialog() {
        ColorPickerDialog.Builder(context)
            .setTitle("ColorPicker Dialog")
            .setPreferenceName("MyColorPickerDialog")
            .setPositiveButton("confirm",
                ColorEnvelopeListener { envelope, _ ->
                    setHueLightColor(envelope)
                })
            .setNegativeButton("cancle") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachAlphaSlideBar(true)
            .attachBrightnessSlideBar(true)
            .show()
    }

    private fun setHueLightColor(envelope: ColorEnvelope) {
        val color = envelope.color
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0

        val x = r * 0.649926 + g * 0.103455 + b * 0.197109
        val y = r * 0.234327 + g * 0.743075 + b * 0.022598

        val url = "https://api.meethue.com/bridge/$hueUsername/lights/$lightId/state"
        println("Request URL: $url")
        val jsonBody = JSONObject()
        jsonBody.put("on", true)
        jsonBody.put("xy", JSONArray(listOf(x, y)))

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = okhttp3.Request.Builder()
            .url(url)
            .put(requestBody)
            .header("Authorization", "Bearer $access_token")
            .header("Content-Type", "application/json")
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "색상이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                        saveColorToFirebase(color)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "색상 변경 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "색상 변경 중 오류 발생", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun saveColorToFirebase(color: Int) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return
        val database = FirebaseDatabase.getInstance().getReference("users/$userId/lights/$lightId")

        database.child("Mycolor").setValue(color).addOnSuccessListener {
            Toast.makeText(context, "색상이 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "색상 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }


    fun showDeleteConfirmationDialog() {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Delete Light")
            .setMessage("Are you sure you want to delete this light?")
            .setPositiveButton("Yes") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    proceedToDelete()
                }
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
    }

    private suspend fun proceedToDelete() {
        val url = "https://api.meethue.com/bridge/$hueUsername/lights/$lightId/"
        val request = okhttp3.Request.Builder()
            .url(url)
            .delete()
            .build()

        val response = client.newCall(request).execute()

        withContext(Dispatchers.Main) {
            if (response.isSuccessful) {
                Toast.makeText(context, "Light successfully deleted.", Toast.LENGTH_SHORT).show()
                // Navigate to another activity if needed
            } else {
                Toast.makeText(context, "Failed to delete light.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBrightnessSettingDialog() {
        CoroutineScope(Dispatchers.IO).launch {
            val user = FirebaseAuth.getInstance().currentUser
            val userId = user?.uid ?: return@launch  // 로그인한 사용자가 없으면 함수를 빠져나갑니다.
            val database = FirebaseDatabase.getInstance().getReference("users/$userId/lights/$lightId")

            var currentBrightness = 0  // 기본값으로 설정합니다.

            try {
                val snapshot = database.child("MyBri").get().await()
                currentBrightness = snapshot.getValue(Int::class.java) ?: 0  // Firebase에서 가져온 값으로 설정합니다.
            } catch (e: Exception) {
                // 에러 처리
            }

            withContext(Dispatchers.Main) {
                val dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_brightness_setting, null)
                val seekBar = dialogLayout.findViewById<SeekBar>(R.id.seekBar)
                val brightnessValueTextView = dialogLayout.findViewById<TextView>(R.id.brightness_value_text_view)

                seekBar.progress = currentBrightness
                brightnessValueTextView.text = currentBrightness.toString()

                // SeekBar의 값이 변경되면 TextView도 업데이트
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        brightnessValueTextView.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                val alertDialog = AlertDialog.Builder(context)
                    .setView(dialogLayout)
                    .setPositiveButton("저장") { _, _ ->
                        val selectedBrightness = seekBar.progress
                        saveBrightnessToFirebase(selectedBrightness)
                    }
                    .setNegativeButton("취소") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()

                alertDialog.show()
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK)
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
            }
        }
    }

    private fun saveBrightnessToFirebase(brightness: Int) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return  // 로그인한 사용자가 없으면 함수를 빠져나갑니다.

        // lightId는 이 클래스에 이미 있는 변수로, 해당 조명의 ID입니다.
        val lightId = lightId

        val database = FirebaseDatabase.getInstance().getReference("users/$userId/lights/$lightId/")

        database.child("MyBri").setValue(brightness).addOnSuccessListener {
            Toast.makeText(context, "밝기가 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "밝기 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }
}