package com.example.airquality

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "lights")
data class Light(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)