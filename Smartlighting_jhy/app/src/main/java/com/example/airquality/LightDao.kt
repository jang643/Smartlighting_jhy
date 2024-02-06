package com.example.airquality

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LightDao {
    @get:Query(value = "SELECT * FROM lights")
    val allLights: List<Light>

    @Insert
    fun insert(light: Light): Long

    @Delete
    fun delete(light: Light)
}