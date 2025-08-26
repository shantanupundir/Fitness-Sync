package com.example.fitnessapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.fitnessapp.R
import com.example.fitnessapp.data.repository.FitnessRepository
import com.example.fitnessapp.databinding.ActivityMainBinding
import com.example.fitnessapp.ui.ViewModel.FitnessViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var currentLocationMarker: Marker
    private var firstLocationUpdate = true
    private var isWorkoutPaused = false
    private lateinit var pathPolyline: Polyline

    private val viewModel: FitnessViewModel by viewModels {
        FitnessViewModel.Factory(FitnessRepository(this), this)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            viewModel.startWorkout()
        }
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        map = binding.map
        setupMap()
        setUpObservers()
        setupButtons()
        setupWeightInput()
    }

    private fun setupButtons() {
        binding.actionButton.setOnClickListener {
            if (viewModel.isTracking.value != true) {
                val weight = binding.weightInput.text.toString().toFloatOrNull()
                if (weight == null || weight <= 40) {
                    binding.weightInput.error = "Please enter a valid weight"
                    return@setOnClickListener
                }
                checkPermissionAndStartTracking()
                binding.pauseResumeButton.visibility = View.VISIBLE
            } else {
                stopTracking()
                binding.pauseResumeButton.visibility = View.GONE
            }
        }
        binding.pauseResumeButton.setOnClickListener {
            if (isWorkoutPaused) {
                resumeTracking()
            } else {
                pauseTracking()
            }
        }
    }

    private fun setupWeightInput() {
        binding.weightInput.setOnEditorActionListener { _, _, _ ->
            val weight = binding.weightInput.text.toString().toFloatOrNull()
            if (weight != null && weight > 0) {
                viewModel.updateWeight(weight)
            }
            false
        }
    }

    private fun pauseTracking() {
        isWorkoutPaused = true
        binding.pauseResumeButton.text = "RESUME"
        viewModel.pauseWorkout()
    }

    private fun resumeTracking() {
        isWorkoutPaused = false
        binding.pauseResumeButton.text = "PAUSE"
        viewModel.resumeWorkout()
    }

    private fun setupMap() {
        map.apply {
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            setTileSource(TileSourceFactory.MAPNIK)
        }
        currentLocationMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.baseline_location_on_24
            )
            title = "current location"
        }
        map.overlays.add(currentLocationMarker)
        pathPolyline = Polyline(map).apply {
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(pathPolyline)
        startLocationUpdates()
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissionAndStartTracking() {
        if (checkLocationPermission()) {
            viewModel.startWorkout()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationUpdates() {
        if (!checkLocationPermission()) return

        val locationRequest: LocationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 1000
            fastestInterval = 500
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    updateLocationMarker(latLng)
                }
            }
        }

        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun updateLocationMarker(latLng: LatLng) {
        currentLocationMarker.position = GeoPoint(latLng.latitude, latLng.longitude)
        if (firstLocationUpdate) {
            map.controller.setCenter(currentLocationMarker.position)
            firstLocationUpdate = false
        }
        map.invalidate()
    }

    private fun setUpObservers() {
        viewModel.currentLocation.observe(this) { latLng ->
            latLng?.let {
                updateLocationMarker(it)
                pathPolyline.addPoint(GeoPoint(it.latitude, it.longitude))
                map.invalidate()
            }
        }

        viewModel.distance.observe(this) {
            binding.distanceValue.text = viewModel.formatDistance(it)
        }

        viewModel.calories.observe(this) {
            binding.caloriesValue.text = viewModel.formatCalories(it)
        }

        viewModel.duration.observe(this) {
            binding.durationValue.text = viewModel.formatDuration(it)
        }
    }

    private fun stopTracking() {
        viewModel.stopWorkout()
    }
}
