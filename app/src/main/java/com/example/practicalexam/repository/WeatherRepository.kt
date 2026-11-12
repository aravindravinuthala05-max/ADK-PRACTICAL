package com.example.practicalexam.repository

import com.example.practicalexam.model.CurrentWeather
import com.example.practicalexam.model.DailyForecast
import com.example.practicalexam.model.WeatherResult
import com.example.practicalexam.util.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository(private val prefManager: PrefManager) {

    suspend fun fetchWeather(lat: Double, lon: Double): Result<WeatherResult> =
        withContext(Dispatchers.IO) {
            val cacheKey = String.format(Locale.US, "%.2f_%.2f", lat, lon)
            val urlString = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto",
                lat,
                lon
            )

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    prefManager.cacheWeatherData(cacheKey, jsonText)
                    val result = parseWeatherJson(jsonText, lat, lon, isCached = false)
                    Result.success(result)
                } else {
                    loadFromCacheOrError(cacheKey, lat, lon, Exception("HTTP error code: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                loadFromCacheOrError(cacheKey, lat, lon, e)
            }
        }

    private fun loadFromCacheOrError(
        cacheKey: String,
        lat: Double,
        lon: Double,
        originalException: Exception
    ): Result<WeatherResult> {
        val cachedJson = prefManager.getCachedWeatherData(cacheKey)
        return if (!cachedJson.isNullOrEmpty()) {
            try {
                val result = parseWeatherJson(cachedJson, lat, lon, isCached = true)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(originalException)
            }
        } else {
            Result.failure(originalException)
        }
    }

    private fun parseWeatherJson(
        jsonText: String,
        lat: Double,
        lon: Double,
        isCached: Boolean
    ): WeatherResult {
        val root = JSONObject(jsonText)

        // Parse Current Weather
        val currentObj = root.getJSONObject("current")
        val temp = currentObj.optDouble("temperature_2m", 0.0)
        val code = currentObj.optInt("weather_code", 0)
        val wind = currentObj.optDouble("wind_speed_10m", 0.0)
        val currentWeather = CurrentWeather(temp, code, wind)

        // Parse 7-Day Forecast
        val dailyObj = root.optJSONObject("daily")
        val dailyForecasts = mutableListOf<DailyForecast>()

        if (dailyObj != null) {
            val timeArray = dailyObj.optJSONArray("time")
            val maxArray = dailyObj.optJSONArray("temperature_2m_max")
            val minArray = dailyObj.optJSONArray("temperature_2m_min")
            val codeArray = dailyObj.optJSONArray("weather_code")

            if (timeArray != null) {
                val count = timeArray.length()
                for (i in 0 until count) {
                    val timeStr = timeArray.optString(i, "")
                    val tMax = maxArray?.optDouble(i, 0.0) ?: 0.0
                    val tMin = minArray?.optDouble(i, 0.0) ?: 0.0
                    val dCode = codeArray?.optInt(i, code) ?: code
                    dailyForecasts.add(DailyForecast(timeStr, tMax, tMin, dCode))
                }
            }
        }

        return WeatherResult(
            latitude = lat,
            longitude = lon,
            currentWeather = currentWeather,
            dailyForecasts = dailyForecasts,
            isCached = isCached
        )
    }
}