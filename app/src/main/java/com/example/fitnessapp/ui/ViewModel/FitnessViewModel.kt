package com.example.fitnessapp.ui.ViewModel

import android.content.Context
import androidx.lifecycle.*
import com.example.fitnessapp.data.repository.FitnessRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FitnessViewModel(
    private val repository: FitnessRepository,
    private val context: Context
) : ViewModel() {

    private val _userWeight = MutableLiveData<Float>(70f)
    val currentLocation: LiveData<LatLng?> = repository.currentLocation

    private val _distance = MutableLiveData<Float>(0f)
    val distance: LiveData<Float> = _distance

    private val _calories = MutableLiveData<Float>(0f)
    val calories: LiveData<Float> = _calories

    private val _isTracking = MutableLiveData<Boolean>(false)
    val isTracking: LiveData<Boolean> = _isTracking

    private val _duration = MutableLiveData<Long>(0L)
    val duration: LiveData<Long> = _duration

    private val _pace = MutableLiveData<Double>(0.0)
    val pace: LiveData<Double> = _pace

    val routePoints: LiveData<List<LatLng>> = repository.routePoints
    private var durationUpdateJob: Job? = null
    private var activeTime: Long = 0
    private var lastUpdateTime: Long = 0

    init {
        repository.totalDistance.observeForever { newDistance ->
            _distance.value = newDistance
            updateCalories()
            updatePace()
        }
        repository.isTracking.observeForever { isTracking ->
            _isTracking.value = isTracking
            if (!isTracking) {
                lastUpdateTime = 0
            }
        }
    }

    fun startWorkout() {
        viewModelScope.launch {
            _duration.value = 0
            _calories.value = 0f
            _distance.value = 0f
            activeTime = 0
            lastUpdateTime = System.currentTimeMillis()
            _isTracking.value = true
            repository.startTracking()
            startDurationUpdate()
        }
    }

    fun updateWeight(weight: Float) {
        _userWeight.value = weight
        updateCalories()
    }

    private fun startDurationUpdate() {
        durationUpdateJob?.cancel()
        durationUpdateJob = viewModelScope.launch {
            while (isActive && _isTracking.value == true) {
                val now = System.currentTimeMillis()
                _duration.value = activeTime + (now-lastUpdateTime)
                updateCalories()
                delay(1000)
            }
        }
    }

    private fun updateCalories() {
        val weight = _userWeight.value ?: 70f
        val distance = _distance.value ?: 0f
        val duration = _duration.value ?: 0L
        val newCalories = repository.calculateCalories(weight, distance, duration)
        _calories.value = newCalories
    }

    private fun updatePace() {
        val distance = _distance.value ?: 0f
        val duration = _duration.value ?: 0L
        if (distance > 0) {
            _pace.value = repository.calculatePace(distance, duration)
        }
    }

    fun stopWorkout() {
        _isTracking.value = false
        repository.stopTracking()
        stopDurationUpdate()
        lastUpdateTime = 0
        activeTime = 0
    }

    fun pauseWorkout() {
        _isTracking.value = false
        repository.stopTracking()
        stopDurationUpdate()
        val now = System.currentTimeMillis()
        activeTime += (now-lastUpdateTime)
        lastUpdateTime = 0
    }

    fun resumeWorkout() {
        viewModelScope.launch {
            lastUpdateTime = System.currentTimeMillis()
            _isTracking.value = true
            repository.startTracking()
            startDurationUpdate()
        }
    }

    fun clearWorkout() {
        _distance.value = 0f
        _calories.value = 0f
        _duration.value = 0L
        _pace.value = 0.0
        activeTime = 0
        lastUpdateTime = 0
        repository.clearTracking()
    }

    fun formatDuration(duration: Long): String {
        val seconds: Long = (duration / 1000) % 60
        val minutes: Long = (duration / (1000 * 60)) % 60
        val hours: Long = duration / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatPace(pace: Double): String {
        return String.format("%.2f km/h", pace)
    }

    fun formatDistance(distance: Float): String {
        return String.format("%.2f km", distance)
    }

    fun formatCalories(calories: Float): String {
        return String.format("%.0f kcal", calories)
    }

    private fun stopDurationUpdate() {
        durationUpdateJob?.cancel()
        durationUpdateJob = null
    }

    class Factory(
        private val repository: FitnessRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FitnessViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FitnessViewModel(repository, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
