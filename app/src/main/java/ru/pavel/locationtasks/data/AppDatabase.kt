package ru.pavel.locationtasks.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        GeofenceLogEntity::class,
        PlaceEntity::class,
        CategoryEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun geofenceLogDao(): GeofenceLogDao
    abstract fun placeDao(): PlaceDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        val SEED_CATEGORIES_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                insertDefaultCategories(db)
            }
        }

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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS places (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT,
                        address TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        radiusMeters REAL NOT NULL,
                        lastUsedAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_places_name ON places (name)",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_places_latitude_longitude
                    ON places (latitude, longitude)
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_places_lastUsedAt ON places (lastUsedAt)",
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO places (
                        name,
                        address,
                        latitude,
                        longitude,
                        radiusMeters,
                        lastUsedAt,
                        createdAt
                    )
                    SELECT
                        NULL,
                        COALESCE(address, ''),
                        latitude,
                        longitude,
                        geofenceRadiusMeters,
                        MAX(updatedAt),
                        MIN(createdAt)
                    FROM tasks
                    WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                    GROUP BY latitude, longitude
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT 'NONE'",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN tags TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN checklistData TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN archivedAt INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                insertDefaultCategories(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN recurrenceDaysMask INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDayOfMonth INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceAnchorAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceEndAt INTEGER")
                db.execSQL(
                    """
                    UPDATE tasks
                    SET recurrenceAnchorAt = dueAt
                    WHERE recurrence != 'NONE' AND dueAt IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO categories (
                    id, name, colorArgb, sortOrder, createdAt, updatedAt
                ) VALUES
                    ('SHOPPING', '', ${CategoryEntity.SHOPPING_COLOR}, 0, 0, 0),
                    ('WORK', '', ${CategoryEntity.WORK_COLOR}, 1, 0, 0),
                    ('HOME', '', ${CategoryEntity.HOME_COLOR}, 2, 0, 0)
                """.trimIndent(),
            )
        }
    }
}
