package com.example.practicalexam.model

data class City(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false
)