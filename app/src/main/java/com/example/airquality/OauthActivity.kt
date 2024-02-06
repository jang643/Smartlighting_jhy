package com.example.airquality

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class OauthActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 여기에서 레이아웃을 설정할 수 있습니다. setContentView(R.layout.activity_oauth)

        // OAuth 인증 URL
        val oauthUrl = "https://api.meethue.com/oauth2/auth?clientid=${BuildConfig.CLIENT_ID}&appid=smartlighting&deviceid=smartlighting&devicename=smartlightinghue&state=any&response_type=code"

        // 시스템 브라우저를 통해 인증을 시작
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
        startActivity(browserIntent)
    }
}
