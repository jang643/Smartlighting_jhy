package com.example.airquality

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MyBackgroundWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private var hueUsername: String? = null
    private var access_token: String? = null
    private var turnOnAlarm: String? = null
    private var turnOffAlarm: String? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return@withContext Result.failure()
        val database = FirebaseDatabase.getInstance()
        val databaseRef = database.getReference("users").child(userId)

        try {
            val snapshot = databaseRef.get().await()
            hueUsername = snapshot.child("bridgeApiKey").getValue(String::class.java)
            access_token = snapshot.child("accessToken").getValue(String::class.java)
            turnOnAlarm = snapshot.child("TurnOnAlarm").getValue(String::class.java)
            turnOffAlarm = snapshot.child("TurnOffAlarm").getValue(String::class.java)

            // TODO: 여기에서 알람 정보를 가지고 원하는 작업을 수행하면 됩니다.
            // 예를 들어, 특정 URL로 HTTP 요청을 보낼 수 있습니다.
            // 이 부분은 별도의 함수로 분리하거나, 적절한 로직을 구현해야 합니다.

            // 작업이 성공적으로 완료되면,
            return@withContext Result.success()
        } catch (e: Exception) {
            // 오류 발생 시,
            return@withContext Result.failure()
        }
    }
}
