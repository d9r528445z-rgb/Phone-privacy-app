package com.familyguard.child.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.familyguard.child.data.dao.LocationDao
import com.familyguard.child.data.dao.UsageDao
import com.familyguard.child.data.dao.WebVisitDao
import com.familyguard.child.data.model.AppUsageRecord
import com.familyguard.child.data.model.LocationRecord
import com.familyguard.child.data.model.WebVisitRecord

@Database(
    entities = [AppUsageRecord::class, LocationRecord::class, WebVisitRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun locationDao(): LocationDao
    abstract fun webVisitDao(): WebVisitDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "familyguard.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
