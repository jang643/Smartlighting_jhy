package com.example.airquality.data.model

data class Tokendata(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String  // 이 부분이 추가되어야 함
)