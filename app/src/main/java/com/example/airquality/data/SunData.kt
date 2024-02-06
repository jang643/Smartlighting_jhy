package com.example.airquality.data

data class SunriseSunsetResponse(
    val results: SunData,
    val status: String
)

data class SunData(
    val sunrise: String,
    val sunset: String
    // 여기에 필요한 다른 필드를 추가할 수 있습니다.
)
