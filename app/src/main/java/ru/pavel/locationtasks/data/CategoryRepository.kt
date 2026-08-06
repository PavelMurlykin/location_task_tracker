package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun save(
        id: String?,
        name: String,
        colorArgb: Int,
        now: Long = System.currentTimeMillis(),
    ): CategorySaveResult {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return CategorySaveResult.EMPTY_NAME
        if (normalizedName.length > MAX_CATEGORY_NAME_LENGTH) {
            return CategorySaveResult.NAME_TOO_LONG
        }
        val duplicate = categoryDao.getByName(normalizedName)
        if (duplicate != null && duplicate.id != id) return CategorySaveResult.DUPLICATE_NAME

        val existing = id?.let { categoryDao.getById(it) }
        if (existing == null) {
            categoryDao.insert(
                CategoryEntity(
                    id = UUID.randomUUID().toString(),
                    name = normalizedName,
                    colorArgb = colorArgb,
                    sortOrder = categoryDao.nextSortOrder(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            categoryDao.update(
                existing.copy(
                    name = normalizedName,
                    colorArgb = colorArgb,
                    updatedAt = now,
                ),
            )
        }
        return CategorySaveResult.SAVED
    }

    companion object {
        const val MAX_CATEGORY_NAME_LENGTH = 40
    }
}

enum class CategorySaveResult {
    SAVED,
    EMPTY_NAME,
    NAME_TOO_LONG,
    DUPLICATE_NAME,
}
