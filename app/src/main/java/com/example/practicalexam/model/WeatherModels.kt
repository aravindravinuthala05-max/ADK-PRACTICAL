package com.example.practicalexam.model

data class CurrentWeather(
    val temperature: Double,
    val weatherCode: Int,
    val windSpeed: Double
)

data class DailyForecast(
    val dateString: String,
    val tempMax: Double,
    val tempMin: Double,
    val weatherCode: Int
)

data class WeatherResult(
    val latitude: Double,
    val longitude: Double,
    val currentWeather: CurrentWeather,
    val dailyForecasts: List<DailyForecast>,
    val isCached: Boolean = false
)