package org.happycode.karoo.forumslader.framework.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerDatabaseMigrationTest {

    private val dbName = "migration_test.db"
    private var migratedDb: ForumsladerDatabase? = null

    @After
    fun tearDown() {
        migratedDb?.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `should migrate v1 database to v2 without losing rider_gradient_profile data`() {
        runBlocking {
            // given: existing v1 SQLite database with rider_gradient_profile populated
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(dbName)
            val dbFile = context.getDatabasePath(dbName)
            dbFile.parentFile?.mkdirs()

            SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { v1Db ->
                v1Db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rider_gradient_profile` (" +
                        "`gradientPct` INTEGER NOT NULL, " +
                        "`speedMetersPerSecond` REAL NOT NULL, " +
                        "`frequencyHz` REAL NOT NULL, " +
                        "`dynamoPowerWatts` REAL NOT NULL, " +
                        "PRIMARY KEY(`gradientPct`))"
                )
                v1Db.execSQL(
                    "INSERT INTO `rider_gradient_profile` (`gradientPct`, `speedMetersPerSecond`, `frequencyHz`, `dynamoPowerWatts`) " +
                        "VALUES (8, 2.1, 15.0, 1.2)"
                )
                v1Db.version = 1
            }

            // when: opening Room v2 database with MIGRATION_1_2
            val db = Room.databaseBuilder(context, ForumsladerDatabase::class.java, dbName)
                .addMigrations(ForumsladerDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
            migratedDb = db

            val existingProfiles = db.riderProfileDao().findAll().map { it.toDomain() }

            // then: existing rider profile rows remain intact
            existingProfiles shouldBe listOf(
                GradientProfile(
                    gradientPct = 8,
                    speedMetersPerSecond = 2.1f,
                    frequencyHz = 15.0f,
                    dynamoPowerWatts = 1.2f
                )
            )

            // and: new battery_health_history table supports same-day upsert and pruning
            val dao = db.batteryHealthDao()
            dao.upsert(BatteryHealthEntity(epochDay = 100L, timestampEpochMs = 100L * 86_400_000L, cycleCount = 10, fullChargeCapacityMah = 1190, socPct = 80))
            dao.upsert(BatteryHealthEntity(epochDay = 100L, timestampEpochMs = 100L * 86_400_000L + 5000L, cycleCount = 11, fullChargeCapacityMah = 1185, socPct = 85))
            dao.upsert(BatteryHealthEntity(epochDay = 200L, timestampEpochMs = 200L * 86_400_000L, cycleCount = 20, fullChargeCapacityMah = 1170, socPct = 90))

            dao.findAll().size shouldBe 2
            dao.deleteOlderThan(cutoffEpochMs = 150L * 86_400_000L)
            val remaining = dao.findAll()
            remaining.size shouldBe 1
            remaining.first().epochDay shouldBe 200L
            remaining.first().fullChargeCapacityMah shouldBe 1170
        }
    }
}
