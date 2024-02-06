package com.example.airquality.data

data class User(
    val lights: List<Light> = listOf(),
    val bridgeApiKey: String = "",
    val bridgeIp: String = "",
    val email: String = "",
    val username: String = "",
    val accessToken: String = ""
)

data class Light(
    val MyBri: Int = 0,
    val Mycolor: Int = 0,
    val state: Boolean = false,
    val lightId: String = ""  // lightId 추가
)

data class TurnOnAlarm(
    val on: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0,
    val AlarmId: String = "",
    val isTurnOn: Map<String, Boolean> = mutableMapOf()
)

data class TurnOffAlarm(
    val on: Boolean = false,
    val hour: Int = 0,
    val minute: Int = 0,
    val AlarmId: String = "",
    val isTurnOff: Map<String, Boolean> = mutableMapOf()
)


