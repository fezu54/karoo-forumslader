package org.happycode.karoo.forumslader.framework.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RiderProfileEntity::class], version = 1, exportSchema = false)
abstract class ForumsladerDatabase : RoomDatabase() {
    abstract fun riderProfileDao(): RiderProfileDao

    companion object {
        @Volatile
        private var instance: ForumsladerDatabase? = null

        fun getInstance(context: Context): ForumsladerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ForumsladerDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }

        private const val DATABASE_NAME = "forumslader_rider_profile.db"
    }
}
