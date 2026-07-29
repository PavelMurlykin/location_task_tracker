package ru.pavel.locationtasks.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, GeofenceLogEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun geofenceLogDao(): GeofenceLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN geofenceStatus TEXT NOT NULL DEFAULT 'DISABLED'",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN geofenceStatusDetails TEXT",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN geofenceRegisteredAt INTEGER",
                )
                db.execSQL(
                    """
                    UPDATE tasks
                    SET geofenceStatus = 'PENDING'
                    WHERE geofenceEnabled = 1
                      AND isCompleted = 0
                      AND latitude IS NOT NULL
                      AND longitude IS NOT NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS geofence_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        taskTitle TEXT NOT NULL,
                        event TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        details TEXT,
                        occurredAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_geofence_logs_occurredAt ON geofence_logs (occurredAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_geofence_logs_taskId ON geofence_logs (taskId)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN geofenceTransitionMode TEXT NOT NULL DEFAULT 'ENTER'",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN notificationCooldownMinutes INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN allowedDaysMask INTEGER NOT NULL DEFAULT 127",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN reminderWindowStartMinutes INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN reminderWindowEndMinutes INTEGER",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER")
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN skipUntilNextVisit INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN lastNotifiedTransition TEXT",
                )
            }
        }
    }
}
