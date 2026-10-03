package com.jynx.scrollite.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReelEntry::class, UsageSession::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reelDao(): ReelDao

    abstract fun usageSessionDao(): UsageSessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v2 adds usage_sessions; reel_entries is untouched so saved Reels survive the upgrade. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`section` TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_startMs` ON `usage_sessions` (`startMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_sessions_endMs` ON `usage_sessions` (`endMs`)")
            }
        }

        /** v3 renames the Instagram-specific REELS section to the generic SHORT_VIDEO. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE usage_sessions SET section = 'SHORT_VIDEO' WHERE section = 'REELS'")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scrollite.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
