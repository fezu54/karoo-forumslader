package org.happycode.karoo.forumslader.framework.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RiderProfileEntity::class, BatteryHealthEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ForumsladerDatabase : RoomDatabase() {
    abstract fun riderProfileDao(): RiderProfileDao
    abstract fun batteryHealthDao(): BatteryHealthDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `battery_health_history` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`timestampEpochMs` INTEGER NOT NULL, " +
                        "`cycleCount` INTEGER NOT NULL, " +
                        "`fullChargeCapacityMah` INTEGER NOT NULL, " +
                        "`socPct` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epochDay`))"
                )
            }
        }

        @Volatile
        private var instance: ForumsladerDatabase? = null

        fun getInstance(context: Context): ForumsladerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ForumsladerDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private const val DATABASE_NAME = "forumslader_rider_profile.db"
    }
}
