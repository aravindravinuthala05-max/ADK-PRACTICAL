package com.example.practicalexam.util

import android.content.Context
import android.content.SharedPreferences
import com.example.practicalexam.model.City
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs

class PrefManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weather_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_IS_CELSIUS = "key_is_celsius"
        private const val KEY_FAVOURITES = "key_favourites"
        private const val KEY_CACHE_PREFIX = "cache_weather_"
        private const val KEY_USER_EMAIL = "key_user_email"
    }

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var isCelsius: Boolean
        get() = prefs.getBoolean(KEY_IS_CELSIUS, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_CELSIUS, value).apply()

    fun getFavourites(): List<City> {
        val json = prefs.getString(KEY_FAVOURITES, null)
        return if (json.isNullOrEmpty()) {
            val defaultCities = listOf(
                City("london", "London", 51.5074, -0.1278),
                City("new_york", "New York", 40.7128, -74.0060),
                City("tokyo", "Tokyo", 35.6762, 139.6503)
            )
            saveFavourites(defaultCities)
            defaultCities
        } else {
            try {
                val type = object : TypeToken<List<City>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun saveFavourites(cities: List<City>) {
        val json = gson.toJson(cities)
        prefs.edit().putString(KEY_FAVOURITES, json).apply()
    }

    fun addFavourite(city: City): Boolean {
        val current = getFavourites().toMutableList()
        if (current.none { it.id == city.id || (it.latitude == city.latitude && it.longitude == city.longitude) }) {
            current.add(city)
            saveFavourites(current)
            return true
        }
        return false
    }

    fun removeFavourite(cityId: String) {
        val current = getFavourites().toMutableList()
        val updated = current.filterNot { it.id == cityId }
        saveFavourites(updated)
    }

    fun isFavourite(lat: Double, lon: Double): Boolean {
        return getFavourites().any {
            abs(it.latitude - lat) < 0.01 && abs(it.longitude - lon) < 0.01
        }
    }

    fun cacheWeatherData(key: String, json: String) {
        prefs.edit().putString(KEY_CACHE_PREFIX + key, json).apply()
    }

    fun getCachedWeatherData(key: String): String? {
        return prefs.getString(KEY_CACHE_PREFIX + key, null)
    }
}