package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRepositoryTest {
    @Test
    fun `creates category with normalized name and selected color`() = runBlocking {
        val dao = FakeCategoryDao()
        val repository = CategoryRepository(dao)

        val result = repository.save(null, "  Здоровье  ", 0xFFE53935.toInt(), now = 42)

        assertEquals(CategorySaveResult.SAVED, result)
        assertEquals("Здоровье", dao.snapshot().single().name)
        assertEquals(0xFFE53935.toInt(), dao.snapshot().single().colorArgb)
        assertEquals(42, dao.snapshot().single().createdAt)
    }

    @Test
    fun `editing category keeps identity and changes name and color`() = runBlocking {
        val original = CategoryEntity("one", "Работа", 1, 3, 10, 10)
        val dao = FakeCategoryDao(listOf(original))
        val repository = CategoryRepository(dao)

        val result = repository.save("one", "Проекты", 2, now = 20)

        assertEquals(CategorySaveResult.SAVED, result)
        assertEquals(
            original.copy(name = "Проекты", colorArgb = 2, updatedAt = 20),
            dao.snapshot().single(),
        )
    }

    @Test
    fun `duplicate names are rejected ignoring case`() = runBlocking {
        val dao = FakeCategoryDao(listOf(CategoryEntity("one", "Дом", 1, 0)))
        val repository = CategoryRepository(dao)

        val result = repository.save(null, "дОМ", 2)

        assertEquals(CategorySaveResult.DUPLICATE_NAME, result)
        assertEquals(1, dao.snapshot().size)
    }
}

private class FakeCategoryDao(
    initialCategories: List<CategoryEntity> = emptyList(),
) : CategoryDao {
    private val categories = MutableStateFlow(initialCategories)

    override fun observeAll(): Flow<List<CategoryEntity>> = categories
    override suspend fun getById(id: String) = categories.value.firstOrNull { it.id == id }
    override suspend fun getByName(name: String) = categories.value.firstOrNull {
        it.name.equals(name, ignoreCase = true)
    }
    override suspend fun getAllForBackup() = categories.value
    override suspend fun nextSortOrder() = (categories.value.maxOfOrNull { it.sortOrder } ?: -1) + 1
    override suspend fun insert(category: CategoryEntity) {
        categories.value += category
    }
    override suspend fun insertAll(categories: List<CategoryEntity>) {
        this.categories.value = categories
    }
    override suspend fun update(category: CategoryEntity) {
        categories.value = categories.value.map { if (it.id == category.id) category else it }
    }
    override suspend fun deleteAll() {
        categories.value = emptyList()
    }

    fun snapshot(): List<CategoryEntity> = categories.value
}
