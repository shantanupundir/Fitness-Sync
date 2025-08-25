package com.example.fitnessapp.data.models

import com.google.android.gms.maps.model.LatLng

data class WorkoutSession(
    val id:String,
    val distance:Float,
    val duration: Long,
    val caloriesBurned:Float,
    val routePoints:List<LatLng>,
    val averagePace:Double,
    val timestamp:Long

)
