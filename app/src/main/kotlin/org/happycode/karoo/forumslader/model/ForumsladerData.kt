package org.happycode.karoo.forumslader.model

data class ForumsladerData(
    // electronics and power management
    val batteryVoltage: Float,     
    val batteryCurrent: Float,
    val consumerCurrent: Float,    
    val batteryLevelPct: Int,      

    // Driving dynamics (natively calculated from the dynamo frequency)
    val speedKmh: Float,           
    val tripDistanceKm: Float,     
    val totalDistanceKm: Float,   

    // environmental data
    val temperatureCelsius: Float,
    val altitudeMeters: Float 
)
