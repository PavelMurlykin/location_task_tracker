package ru.pavel.locationtasks.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val NO_CATEGORY_ID = "NONE"
        const val SHOPPING_ID = "SHOPPING"
        const val WORK_ID = "WORK"
        const val HOME_ID = "HOME"

        const val SHOPPING_COLOR = 0xFF43A047.toInt()
        const val WORK_COLOR = 0xFF1E88E5.toInt()
        const val HOME_COLOR = 0xFFFB8C00.toInt()

        fun legacyDefaults(now: Long = System.currentTimeMillis()): List<CategoryEntity> = listOf(
            CategoryEntity(SHOPPING_ID, "", SHOPPING_COLOR, 0, now, now),
            CategoryEntity(WORK_ID, "", WORK_COLOR, 1, now, now),
            CategoryEntity(HOME_ID, "", HOME_COLOR, 2, now, now),
        )
    }
}

val CATEGORY_COLOR_PALETTE: List<Int> = listOf(
    0xFFE53935.toInt(),
    0xFFD81B60.toInt(),
    0xFF8E24AA.toInt(),
    0xFF5E35B1.toInt(),
    0xFF1E88E5.toInt(),
    0xFF00897B.toInt(),
    0xFF43A047.toInt(),
    0xFFF9A825.toInt(),
    0xFFFB8C00.toInt(),
    0xFF6D4C41.toInt(),
)
