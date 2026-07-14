package org.happycode.karoo.forumslader.domain

data class ForumsladerMetrics(
    // electronics and power management
    val batteryVoltage: Float,     
    val batteryCurrent: Float,
    val consumerCurrent: Float,    
    val batteryLevelPct: Int,      

    // Driving dynamics (natively calculated from the dynamo frequency)
    val speedMs: Float,           
    val tripDistanceMeters: Double,     
    val totalDistanceMeters: Double,

    // environmental data
    val temperatureCelsius: Float,
    val altitudeMeters: Float 
)
