package com.example.practicalexam

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.practicalexam.adapter.FavouritesAdapter
import com.example.practicalexam.adapter.ForecastAdapter
import com.example.practicalexam.databinding.ActivityMainBinding
import com.example.practicalexam.databinding.DialogAddCityBinding
import com.example.practicalexam.databinding.DialogAuthBinding
import com.example.practicalexam.model.City
import com.example.practicalexam.model.WeatherResult
import com.example.practicalexam.repository.WeatherRepository
import com.example.practicalexam.util.PrefManager
import com.example.practicalexam.util.WeatherUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PrefManager
    private lateinit var repository: WeatherRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var firebaseAuth: FirebaseAuth? = null
    private var localUserEmail: String? = null

    private lateinit var forecastAdapter: ForecastAdapter
    private lateinit var favouritesAdapter: FavouritesAdapter

    private val locationsList = mutableListOf<City>()
    private var selectedCity: City? = null
    private var lastWeatherResult: WeatherResult? = null

    private var currentLocationCity: City = City(
        id = "current_location",
        name = "Current Location",
        latitude = 51.5074,
        longitude = -0.1278,
        isCurrentLocation = true
    )

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                val idToken: String? = account?.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    localUserEmail = account?.email ?: "Google User"
                    prefManager.userEmail = localUserEmail
                    Toast.makeText(this, "Signed in as $localUserEmail", Toast.LENGTH_SHORT).show()
                    updateAuthUI()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            fetchFusedLocation()
        } else {
            Toast.makeText(this, "Location permission denied. Using default location.", Toast.LENGTH_SHORT).show()
            loadWeatherForSelectedCity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PrefManager(this)
        repository = WeatherRepository(prefManager)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initFirebaseAuth()
        setupUI()
        setupRecyclerViews()
        setupLocationPermissionAndFetch()
    }

    private fun initFirebaseAuth() {
        firebaseAuth = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            try {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:384800486625:android:35b6c8af0ef4a28c7173a0")
                        .setApiKey("AIzaSyDBh3cPDCe1KCjfYhOCuAHzanP11B6fnzY")
                        .setProjectId("weather-forecast-2aeeb")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
                FirebaseAuth.getInstance()
            } catch (ex: Exception) {
                null
            }
        }

        val lastGoogleAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (lastGoogleAccount != null) {
            localUserEmail = lastGoogleAccount.email ?: "Google User"
            prefManager.userEmail = localUserEmail
        } else if (prefManager.userEmail != null) {
            localUserEmail = prefManager.userEmail
        }

        updateAuthUI()
    }

    private fun updateAuthUI() {
        val user = firebaseAuth?.currentUser
        val emailOrAnon = user?.email ?: localUserEmail ?: prefManager.userEmail

        if (emailOrAnon != null) {
            val shortName = if (emailOrAnon.contains("@")) emailOrAnon else "Guest User ($emailOrAnon)"
            binding.tvUserStatus.text = shortName
            binding.btnAuthToggle.text = "Logout"
        } else if (user != null && user.isAnonymous) {
            binding.tvUserStatus.text = "Guest User (${user.uid.take(5)})"
            binding.btnAuthToggle.text = "Logout"
        } else {
            binding.tvUserStatus.text = "Not Logged In"
            binding.btnAuthToggle.text = "Login"
        }
    }

    private fun setupUI() {
        // Unit Toggle setup
        if (prefManager.isCelsius) {
            binding.toggleGroupUnit.check(R.id.btnUnitCelsius)
        } else {
            binding.toggleGroupUnit.check(R.id.btnUnitFahrenheit)
        }

        binding.toggleGroupUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isCelsius = (checkedId == R.id.btnUnitCelsius)
                prefManager.isCelsius = isCelsius
                updateTemperatureDisplays()
            }
        }

        // Swipe refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadWeatherForSelectedCity()
        }

        // Auth toggle button
        binding.btnAuthToggle.setOnClickListener {
            if (firebaseAuth?.currentUser != null || localUserEmail != null || prefManager.userEmail != null) {
                firebaseAuth?.signOut()
                try {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(this, gso).signOut()
                } catch (_: Exception) {}
                localUserEmail = null
                prefManager.userEmail = null
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                updateAuthUI()
            } else {
                showAuthDialog()
            }
        }

        // Favourite button toggle
        binding.btnToggleFavorite.setOnClickListener {
            selectedCity?.let { city ->
                val isFav = prefManager.isFavourite(city.latitude, city.longitude)
                if (isFav) {
                    val favs = prefManager.getFavourites()
                    val toRemove = favs.find { abs(it.latitude - city.latitude) < 0.01 && abs(it.longitude - city.longitude) < 0.01 }
                    toRemove?.let { prefManager.removeFavourite(it.id) }
                    Toast.makeText(this, "Removed from favourites", Toast.LENGTH_SHORT).show()
                } else {
                    val newFav = City(
                        id = if (city.isCurrentLocation) "fav_${UUID.randomUUID()}" else city.id,
                        name = if (city.isCurrentLocation) "Current Loc (${String.format(Locale.US, "%.2f, %.2f", city.latitude, city.longitude)})" else city.name,
                        latitude = city.latitude,
                        longitude = city.longitude
                    )
                    prefManager.addFavourite(newFav)
                    Toast.makeText(this, "Added to favourites", Toast.LENGTH_SHORT).show()
                }
                reloadLocationsAndSpinner()
            }
        }

        // Add custom city button
        binding.btnAddCustomCity.setOnClickListener {
            showAddCityDialog()
        }
    }

    private fun setupRecyclerViews() {
        // Forecast Horizontal RecyclerView
        forecastAdapter = ForecastAdapter(emptyList(), prefManager.isCelsius)
        binding.rvForecast.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvForecast.adapter = forecastAdapter

        // Favourites Horizontal RecyclerView
        favouritesAdapter = FavouritesAdapter(
            cities = prefManager.getFavourites(),
            onSelectCity = { city ->
                selectCityInSpinner(city)
            },
            onRemoveCity = { city ->
                prefManager.removeFavourite(city.id)
                Toast.makeText(this, "Removed ${city.name}", Toast.LENGTH_SHORT).show()
                reloadLocationsAndSpinner()
            }
        )
        binding.rvFavourites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFavourites.adapter = favouritesAdapter
    }

    private fun setupLocationPermissionAndFetch() {
        if (hasLocationPermission()) {
            fetchFusedLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun fetchFusedLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        currentLocationCity = City(
                            id = "current_location",
                            name = "Current Location",
                            latitude = location.latitude,
                            longitude = location.longitude,
                            isCurrentLocation = true
                        )
                    }
                    reloadLocationsAndSpinner()
                }
                .addOnFailureListener {
                    reloadLocationsAndSpinner()
                }
        } catch (e: Exception) {
            reloadLocationsAndSpinner()
        }
    }

    private fun reloadLocationsAndSpinner() {
        locationsList.clear()
        locationsList.add(currentLocationCity)
        val favs = prefManager.getFavourites()
        locationsList.addAll(favs)

        favouritesAdapter.updateData(favs)

        val spinnerNames = locationsList.map { city ->
            if (city.isCurrentLocation) {
                "📍 ${city.name} (${String.format("%.2f, %.2f", city.latitude, city.longitude)})"
            } else {
                "⭐ ${city.name}"
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLocations.adapter = adapter

        binding.spinnerLocations.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in locationsList.indices) {
                    selectedCity = locationsList[position]
                    loadWeatherForSelectedCity()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (selectedCity == null && locationsList.isNotEmpty()) {
            selectedCity = locationsList[0]
            binding.spinnerLocations.setSelection(0)
        }
    }

    private fun selectCityInSpinner(city: City) {
        val index = locationsList.indexOfFirst { it.id == city.id || (abs(it.latitude - city.latitude) < 0.01 && abs(it.longitude - city.longitude) < 0.01) }
        if (index >= 0) {
            binding.spinnerLocations.setSelection(index)
        }
    }

    private fun loadWeatherForSelectedCity() {
        val city = selectedCity ?: currentLocationCity
        binding.swipeRefreshLayout.isRefreshing = true

        updateFavoriteButtonIcon(city)

        lifecycleScope.launch {
            val result = repository.fetchWeather(city.latitude, city.longitude)
            binding.swipeRefreshLayout.isRefreshing = false

            result.onSuccess { weatherResult ->
                lastWeatherResult = weatherResult
                renderWeatherResult(city, weatherResult)
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "Error loading weather: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                binding.cardOfflineBanner.visibility = View.VISIBLE
                binding.tvOfflineBanner.text = "❌ Error fetching weather. Check network connection."
            }
        }
    }

    private fun updateFavoriteButtonIcon(city: City) {
        val isFav = prefManager.isFavourite(city.latitude, city.longitude)
        if (isFav) {
            binding.btnToggleFavorite.setImageResource(R.drawable.ic_favorite)
        } else {
            binding.btnToggleFavorite.setImageResource(R.drawable.ic_favorite_border)
        }
    }

    private fun renderWeatherResult(city: City, weather: WeatherResult) {
        val currentWeather = weather.currentWeather
        val isCelsius = prefManager.isCelsius

        binding.tvCurrentCity.text = if (city.isCurrentLocation) "Current Location" else city.name
        binding.tvCurrentCoordinates.text = String.format("Lat: %.4f, Lon: %.4f", weather.latitude, weather.longitude)
        binding.ivCurrentIcon.setImageResource(WeatherUtils.getWeatherIconResId(currentWeather.weatherCode))
        binding.tvCurrentCondition.text = WeatherUtils.getWeatherConditionText(currentWeather.weatherCode)
        binding.tvCurrentTemp.text = WeatherUtils.formatTemperature(currentWeather.temperature, isCelsius)
        binding.tvWindSpeed.text = String.format("💨 Wind Speed: %.1f km/h", currentWeather.windSpeed)

        if (weather.isCached) {
            binding.cardOfflineBanner.visibility = View.VISIBLE
            binding.tvOfflineBanner.text = "⚠️ Offline mode — Showing cached weather data"
        } else {
            binding.cardOfflineBanner.visibility = View.GONE
        }

        forecastAdapter.updateData(weather.dailyForecasts, isCelsius)
    }

    private fun updateTemperatureDisplays() {
        val weather = lastWeatherResult
        val city = selectedCity ?: currentLocationCity
        if (weather != null) {
            renderWeatherResult(city, weather)
        } else {
            forecastAdapter.updateUnit(prefManager.isCelsius)
        }
    }

    private fun showAuthDialog() {
        val dialogBinding = DialogAuthBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnAuthLogin.setOnClickListener {
            val email = dialogBinding.etAuthEmail.text.toString().trim()
            val password = dialogBinding.etAuthPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (firebaseAuth != null) {
                firebaseAuth?.signInWithEmailAndPassword(email, password)
                    ?.addOnSuccessListener {
                        localUserEmail = email
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
                    ?.addOnFailureListener { e ->
                        localUserEmail = email
                        Toast.makeText(this, "Login: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
            } else {
                localUserEmail = email
                Toast.makeText(this, "Logged in as $email", Toast.LENGTH_SHORT).show()
                updateAuthUI()
                dialog.dismiss()
            }
        }

        dialogBinding.btnAuthRegister.setOnClickListener {
            val email = dialogBinding.etAuthEmail.text.toString().trim()
            val password = dialogBinding.etAuthPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address (e.g. user@example.com)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (firebaseAuth != null) {
                firebaseAuth?.createUserWithEmailAndPassword(email, password)
                    ?.addOnSuccessListener {
                        localUserEmail = email
                        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
                    ?.addOnFailureListener { e ->
                        localUserEmail = email
                        Toast.makeText(this, "Registered: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
            } else {
                localUserEmail = email
                Toast.makeText(this, "Registered as $email", Toast.LENGTH_SHORT).show()
                updateAuthUI()
                dialog.dismiss()
            }
        }

        dialogBinding.btnAuthGoogle.setOnClickListener {
            dialog.dismiss()
            signInWithGoogle()
        }

        dialogBinding.btnAuthAnonymous.setOnClickListener {
            if (firebaseAuth != null) {
                firebaseAuth?.signInAnonymously()
                    ?.addOnSuccessListener {
                        localUserEmail = "Guest User"
                        Toast.makeText(this, "Signed in as Guest", Toast.LENGTH_SHORT).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
                    ?.addOnFailureListener { e ->
                        localUserEmail = "Guest User"
                        Toast.makeText(this, "Signed in as Guest", Toast.LENGTH_SHORT).show()
                        updateAuthUI()
                        dialog.dismiss()
                    }
            } else {
                localUserEmail = "Guest User"
                Toast.makeText(this, "Signed in as Guest", Toast.LENGTH_SHORT).show()
                updateAuthUI()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddCityDialog() {
        val dialogBinding = DialogAddCityBinding.inflate(LayoutInflater.from(this))
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val name = dialogBinding.etCityName.text.toString().trim()
                val latStr = dialogBinding.etLatitude.text.toString().trim()
                val lonStr = dialogBinding.etLongitude.text.toString().trim()

                if (name.isNotEmpty() && latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                    val lat = latStr.toDoubleOrNull()
                    val lon = lonStr.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        val newCity = City(
                            id = "city_${UUID.randomUUID()}",
                            name = name,
                            latitude = lat,
                            longitude = lon
                        )
                        prefManager.addFavourite(newCity)
                        reloadLocationsAndSpinner()
                        selectCityInSpinner(newCity)
                        Toast.makeText(this, "Added $name to favourites", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid latitude or longitude", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun signInWithGoogle() {
        val webClientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        if (firebaseAuth != null) {
            firebaseAuth?.signInWithCredential(credential)
                ?.addOnSuccessListener { authResult ->
                    val user = authResult.user
                    localUserEmail = user?.email ?: "Google User"
                    prefManager.userEmail = localUserEmail
                    Toast.makeText(this, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                    updateAuthUI()
                }
                ?.addOnFailureListener { e ->
                    Toast.makeText(this, "Authentication failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } else {
            localUserEmail = "Google User"
            prefManager.userEmail = localUserEmail
            Toast.makeText(this, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
            updateAuthUI()
        }
    }
}