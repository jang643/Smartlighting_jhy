package com.example.airquality.data

// HueDataClasses.kt

import kotlinx.serialization.Serializable

@Serializable
data class WhitelistRequest(val devicetype: String)

@Serializable
data class WhitelistResponse(val success: Map<String, String>?)

@Serializable
data class HueLight(val name: String)

@Serializable
data class AllLightsResponse(val lights: Map<String, HueLight>)
