package org.happycode.karoo.forumslader.domain

interface RideHistoryGateway {
    suspend fun saveSummary(summary: RideEnergySummary)
    suspend fun getHistory(): List<RideEnergySummary>
}
