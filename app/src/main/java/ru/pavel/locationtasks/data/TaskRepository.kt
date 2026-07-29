package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.Flow
import ru.pavel.locationtasks.location.GeofenceCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val geofenceCoordinator: GeofenceCoordinator,
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeById(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)

    suspend fun getById(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun save(task: TaskEntity): Long {
        val now = System.currentTimeMillis()
        val savedTask = if (task.id == 0L) {
            val newTask = task.copy(createdAt = now, updatedAt = now)
            val id = taskDao.insert(newTask)
            newTask.copy(id = id)
        } else {
            val updatedTask = task.copy(updatedAt = now)
            taskDao.update(updatedTask)
            updatedTask
        }

        if (savedTask.shouldMonitor) {
            geofenceCoordinator.reconcileTask(savedTask.id)
        } else {
            geofenceCoordinator.deactivate(savedTask.id)
            geofenceCoordinator.reconcileAll()
        }
        return savedTask.id
    }

    suspend fun setCompleted(task: TaskEntity, completed: Boolean) {
        taskDao.setCompleted(task.id, completed, System.currentTimeMillis())
        if (completed) {
            geofenceCoordinator.deactivate(task.id)
            geofenceCoordinator.reconcileAll()
        } else if (task.copy(isCompleted = false).shouldMonitor) {
            geofenceCoordinator.reconcileTask(task.id)
        }
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val task = taskDao.getById(id) ?: return
        setCompleted(task, completed)
    }

    suspend fun delete(task: TaskEntity) {
        taskDao.delete(task)
        geofenceCoordinator.deactivate(task.id)
        geofenceCoordinator.reconcileAll()
    }
}
