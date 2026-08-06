package ru.pavel.locationtasks.data.backup

import ru.pavel.locationtasks.data.CategoryEntity
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.TaskEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupSnapshot(
    val createdAt: Long,
    val reminderPreferences: ReminderPreferences,
    val tasks: List<TaskEntity>,
    val places: List<PlaceEntity>,
    val categories: List<CategoryEntity> = CategoryEntity.legacyDefaults(createdAt),
)

enum class BackupCodecFailure {
    INVALID_PASSWORD,
    INVALID_FILE,
    UNSUPPORTED_VERSION,
}

class BackupCodecException(
    val failure: BackupCodecFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

object BackupCodec {
    fun encode(
        snapshot: BackupSnapshot,
        password: CharArray,
        secureRandom: SecureRandom = SecureRandom(),
    ): ByteArray {
        validatePassword(password)
        validateSnapshot(snapshot)
        val payload = encodePayload(snapshot)
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val initializationVector = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val keyBytes = deriveKey(password, salt, KDF_ITERATIONS)
        val encrypted = try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(keyBytes, KEY_ALGORITHM),
                    GCMParameterSpec(GCM_TAG_BITS, initializationVector),
                )
                doFinal(payload)
            }
        } finally {
            keyBytes.fill(0)
            payload.fill(0)
        }

        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FILE_VERSION)
                output.writeInt(KDF_ITERATIONS)
                output.write(salt)
                output.write(initializationVector)
                output.writeInt(encrypted.size)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    fun decode(data: ByteArray, password: CharArray): BackupSnapshot {
        validatePassword(password)
        if (data.size !in MIN_FILE_SIZE..MAX_BACKUP_FILE_BYTES) {
            throw BackupCodecException(BackupCodecFailure.INVALID_FILE)
        }
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readInt() != FILE_MAGIC) invalidFile()
                if (input.readInt() != FILE_VERSION) unsupportedVersion()
                val iterations = input.readInt()
                if (iterations !in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) invalidFile()
                val salt = input.readFixedBytes(SALT_SIZE)
                val initializationVector = input.readFixedBytes(IV_SIZE)
                val encryptedSize = input.readInt()
                if (encryptedSize <= GCM_TAG_BYTES || encryptedSize != input.available()) {
                    invalidFile()
                }
                val encrypted = input.readFixedBytes(encryptedSize)
                val keyBytes = deriveKey(password, salt, iterations)
                val payload = try {
                    Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                        init(
                            Cipher.DECRYPT_MODE,
                            SecretKeySpec(keyBytes, KEY_ALGORITHM),
                            GCMParameterSpec(GCM_TAG_BITS, initializationVector),
                        )
                        doFinal(encrypted)
                    }
                } finally {
                    keyBytes.fill(0)
                }
                try {
                    decodePayload(payload)
                } finally {
                    payload.fill(0)
                }
            }
        } catch (exception: BackupCodecException) {
            throw exception
        } catch (exception: AEADBadTagException) {
            throw BackupCodecException(BackupCodecFailure.INVALID_PASSWORD, exception)
        } catch (exception: GeneralSecurityException) {
            throw BackupCodecException(BackupCodecFailure.INVALID_FILE, exception)
        } catch (exception: IOException) {
            throw BackupCodecException(BackupCodecFailure.INVALID_FILE, exception)
        } catch (exception: IllegalArgumentException) {
            throw BackupCodecException(BackupCodecFailure.INVALID_FILE, exception)
        }
    }

    private fun encodePayload(snapshot: BackupSnapshot): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PAYLOAD_MAGIC)
                output.writeInt(PAYLOAD_VERSION)
                output.writeLong(snapshot.createdAt)
                snapshot.reminderPreferences.writeTo(output)
                output.writeInt(snapshot.tasks.size)
                snapshot.tasks.forEach { it.writeTo(output) }
                output.writeInt(snapshot.places.size)
                snapshot.places.forEach { it.writeTo(output) }
                output.writeInt(snapshot.categories.size)
                snapshot.categories.forEach { it.writeTo(output) }
            }
            bytes.toByteArray().also {
                if (it.size > MAX_BACKUP_FILE_BYTES) {
                    throw BackupCodecException(BackupCodecFailure.INVALID_FILE)
                }
            }
        }

    private fun decodePayload(payload: ByteArray): BackupSnapshot =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            if (input.readInt() != PAYLOAD_MAGIC) invalidFile()
            val payloadVersion = input.readInt()
            if (payloadVersion !in MIN_PAYLOAD_VERSION..PAYLOAD_VERSION) unsupportedVersion()
            val createdAt = input.readLong()
            if (createdAt <= 0) invalidFile()
            val preferences = input.readReminderPreferences()
            val taskCount = input.readBoundedCount(MAX_TASKS)
            val tasks = List(taskCount) { input.readTask(payloadVersion) }
            val placeCount = input.readBoundedCount(MAX_PLACES)
            val places = List(placeCount) { input.readPlace() }
            val categories = if (payloadVersion >= 2) {
                val categoryCount = input.readBoundedCount(MAX_CATEGORIES)
                List(categoryCount) { input.readCategory() }
            } else {
                CategoryEntity.legacyDefaults(createdAt)
            }
            if (input.available() != 0) invalidFile()
            BackupSnapshot(createdAt, preferences, tasks, places, categories)
                .also(::validateSnapshot)
        }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun validatePassword(password: CharArray) {
        if (password.size !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            throw BackupCodecException(BackupCodecFailure.INVALID_PASSWORD)
        }
    }

    private fun validateSnapshot(snapshot: BackupSnapshot) {
        if (snapshot.createdAt <= 0 ||
            snapshot.tasks.size > MAX_TASKS ||
            snapshot.places.size > MAX_PLACES ||
            snapshot.categories.size > MAX_CATEGORIES
        ) invalidFile()
        if (snapshot.reminderPreferences.notificationCooldownHours !in
            ru.pavel.locationtasks.data.UserPreferencesRepository.ALLOWED_COOLDOWNS ||
            snapshot.reminderPreferences.quietHoursStartMinutes !in MINUTES_IN_DAY ||
            snapshot.reminderPreferences.quietHoursEndMinutes !in MINUTES_IN_DAY
        ) invalidFile()
        if (snapshot.tasks.map(TaskEntity::id).toSet().size != snapshot.tasks.size ||
            snapshot.tasks.any { task ->
                task.id <= 0 || task.title.isBlank() ||
                    (task.latitude == null) != (task.longitude == null) ||
                    task.latitude?.let { it !in -90.0..90.0 } == true ||
                    task.longitude?.let { it !in -180.0..180.0 } == true ||
                    task.geofenceRadiusMeters !in 100f..1_000f ||
                    task.recurrenceInterval !in
                    ru.pavel.locationtasks.data.MIN_RECURRENCE_INTERVAL..
                    ru.pavel.locationtasks.data.MAX_RECURRENCE_INTERVAL ||
                    task.recurrenceDaysMask !in 0..
                    ru.pavel.locationtasks.data.ALL_WEEK_DAYS_MASK ||
                    task.recurrenceDayOfMonth?.let { it !in 1..31 } == true ||
                    task.recurrenceAnchorAt?.let { it <= 0 } == true ||
                    task.recurrenceEndAt?.let { it <= 0 } == true
            }
        ) invalidFile()
        if (snapshot.places.map(PlaceEntity::id).toSet().size != snapshot.places.size ||
            snapshot.places.any { place ->
                place.id <= 0 || place.latitude !in -90.0..90.0 ||
                    place.longitude !in -180.0..180.0 ||
                    place.radiusMeters !in 100f..1_000f
            } ||
            snapshot.places.map { it.latitude to it.longitude }.toSet().size !=
            snapshot.places.size ||
            snapshot.places.mapNotNull { it.name?.trim()?.lowercase() }
                .filter(String::isNotEmpty)
                .let { it.toSet().size != it.size }
        ) invalidFile()
        if (snapshot.categories.map(CategoryEntity::id).toSet().size != snapshot.categories.size ||
            snapshot.categories.any { category ->
                category.id.isBlank() ||
                    category.name.length > ru.pavel.locationtasks.data.CategoryRepository
                        .MAX_CATEGORY_NAME_LENGTH ||
                    (category.name.isBlank() && category.id !in LEGACY_CATEGORY_IDS)
            } ||
            snapshot.categories.map(CategoryEntity::name)
                .filter(String::isNotBlank)
                .map(String::lowercase)
                .let { it.toSet().size != it.size }
        ) invalidFile()
        val categoryIds = snapshot.categories.mapTo(mutableSetOf(), CategoryEntity::id)
        val hasUnknownCategory = snapshot.tasks.any {
            it.category != CategoryEntity.NO_CATEGORY_ID && it.category !in categoryIds
        }
        if (hasUnknownCategory) invalidFile()
    }

    private fun ReminderPreferences.writeTo(output: DataOutputStream) {
        output.writeInt(notificationCooldownHours)
        output.writeBoolean(quietHoursEnabled)
        output.writeInt(quietHoursStartMinutes)
        output.writeInt(quietHoursEndMinutes)
    }

    private fun DataInputStream.readReminderPreferences() = ReminderPreferences(
        notificationCooldownHours = readInt(),
        quietHoursEnabled = readBoolean(),
        quietHoursStartMinutes = readInt(),
        quietHoursEndMinutes = readInt(),
    )

    private fun TaskEntity.writeTo(output: DataOutputStream) = with(output) {
        writeLong(id)
        writeSizedString(title)
        writeSizedString(description)
        writeNullableLong(dueAt)
        writeSizedString(priority)
        writeSizedString(category)
        writeSizedString(tags)
        writeSizedString(checklistData)
        writeSizedString(recurrence)
        writeInt(recurrenceInterval)
        writeInt(recurrenceDaysMask)
        writeNullableInt(recurrenceDayOfMonth)
        writeNullableLong(recurrenceAnchorAt)
        writeNullableLong(recurrenceEndAt)
        writeBoolean(isCompleted)
        writeBoolean(isArchived)
        writeNullableLong(archivedAt)
        writeNullableDouble(latitude)
        writeNullableDouble(longitude)
        writeNullableString(address)
        writeFloat(geofenceRadiusMeters)
        writeBoolean(geofenceEnabled)
        writeSizedString(geofenceTransitionMode)
        writeNullableInt(notificationCooldownMinutes)
        writeInt(allowedDaysMask)
        writeNullableInt(reminderWindowStartMinutes)
        writeNullableInt(reminderWindowEndMinutes)
        writeNullableLong(snoozedUntil)
        writeBoolean(skipUntilNextVisit)
        writeNullableLong(lastNotifiedAt)
        writeNullableString(lastNotifiedTransition)
        writeSizedString(geofenceStatus)
        writeNullableString(geofenceStatusDetails)
        writeNullableLong(geofenceRegisteredAt)
        writeLong(createdAt)
        writeLong(updatedAt)
    }

    private fun DataInputStream.readTask(payloadVersion: Int): TaskEntity {
        val id = readLong()
        val title = readSizedString()
        val description = readSizedString()
        val dueAt = readNullableLong()
        val priority = readSizedString()
        val category = readSizedString()
        val tags = readSizedString()
        val checklistData = readSizedString()
        val recurrence = readSizedString()
        val recurrenceInterval = if (payloadVersion >= 3) readInt() else 1
        val recurrenceDaysMask = if (payloadVersion >= 3) readInt() else 0
        val recurrenceDayOfMonth = if (payloadVersion >= 3) readNullableInt() else null
        val recurrenceAnchorAt = if (payloadVersion >= 3) readNullableLong() else dueAt
        val recurrenceEndAt = if (payloadVersion >= 3) readNullableLong() else null
        return TaskEntity(
            id = id,
            title = title,
            description = description,
            dueAt = dueAt,
            priority = priority,
            category = category,
            tags = tags,
            checklistData = checklistData,
            recurrence = recurrence,
            recurrenceInterval = recurrenceInterval,
            recurrenceDaysMask = recurrenceDaysMask,
            recurrenceDayOfMonth = recurrenceDayOfMonth,
            recurrenceAnchorAt = recurrenceAnchorAt,
            recurrenceEndAt = recurrenceEndAt,
            isCompleted = readBoolean(),
            isArchived = readBoolean(),
            archivedAt = readNullableLong(),
            latitude = readNullableDouble(),
            longitude = readNullableDouble(),
            address = readNullableString(),
            geofenceRadiusMeters = readFloat(),
            geofenceEnabled = readBoolean(),
            geofenceTransitionMode = readSizedString(),
            notificationCooldownMinutes = readNullableInt(),
            allowedDaysMask = readInt(),
            reminderWindowStartMinutes = readNullableInt(),
            reminderWindowEndMinutes = readNullableInt(),
            snoozedUntil = readNullableLong(),
            skipUntilNextVisit = readBoolean(),
            lastNotifiedAt = readNullableLong(),
            lastNotifiedTransition = readNullableString(),
            geofenceStatus = readSizedString(),
            geofenceStatusDetails = readNullableString(),
            geofenceRegisteredAt = readNullableLong(),
            createdAt = readLong(),
            updatedAt = readLong(),
        )
    }

    private fun PlaceEntity.writeTo(output: DataOutputStream) = with(output) {
        writeLong(id)
        writeNullableString(name)
        writeSizedString(address)
        writeDouble(latitude)
        writeDouble(longitude)
        writeFloat(radiusMeters)
        writeLong(lastUsedAt)
        writeLong(createdAt)
    }

    private fun DataInputStream.readPlace() = PlaceEntity(
        id = readLong(),
        name = readNullableString(),
        address = readSizedString(),
        latitude = readDouble(),
        longitude = readDouble(),
        radiusMeters = readFloat(),
        lastUsedAt = readLong(),
        createdAt = readLong(),
    )

    private fun CategoryEntity.writeTo(output: DataOutputStream) = with(output) {
        writeSizedString(id)
        writeSizedString(name)
        writeInt(colorArgb)
        writeInt(sortOrder)
        writeLong(createdAt)
        writeLong(updatedAt)
    }

    private fun DataInputStream.readCategory() = CategoryEntity(
        id = readSizedString(),
        name = readSizedString(),
        colorArgb = readInt(),
        sortOrder = readInt(),
        createdAt = readLong(),
        updatedAt = readLong(),
    )

    private fun DataOutputStream.writeSizedString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_STRING_BYTES) invalidFile()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(): String {
        val length = readInt()
        if (length !in 0..MAX_STRING_BYTES || length > available()) invalidFile()
        return readFixedBytes(length).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeSizedString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readSizedString() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        value?.let(::writeLong)
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        value?.let(::writeInt)
    }

    private fun DataInputStream.readNullableInt(): Int? =
        if (readBoolean()) readInt() else null

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        value?.let(::writeDouble)
    }

    private fun DataInputStream.readNullableDouble(): Double? =
        if (readBoolean()) readDouble() else null

    private fun DataInputStream.readBoundedCount(maximum: Int): Int = readInt().also {
        if (it !in 0..maximum) invalidFile()
    }

    private fun DataInputStream.readFixedBytes(size: Int): ByteArray = ByteArray(size).also {
        try {
            readFully(it)
        } catch (exception: EOFException) {
            throw BackupCodecException(BackupCodecFailure.INVALID_FILE, exception)
        }
    }

    private fun invalidFile(): Nothing =
        throw BackupCodecException(BackupCodecFailure.INVALID_FILE)

    private fun unsupportedVersion(): Nothing =
        throw BackupCodecException(BackupCodecFailure.UNSUPPORTED_VERSION)

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_BACKUP_FILE_BYTES = 16 * 1024 * 1024
    private const val MAX_PASSWORD_LENGTH = 256
    private const val MAX_STRING_BYTES = 1024 * 1024
    private const val MAX_TASKS = 10_000
    private const val MAX_PLACES = 10_000
    private const val MAX_CATEGORIES = 500
    private val MINUTES_IN_DAY = 0 until 24 * 60
    private const val FILE_MAGIC = 0x4C54424B
    private const val FILE_VERSION = 1
    private const val PAYLOAD_MAGIC = 0x4C544442
    private const val MIN_PAYLOAD_VERSION = 1
    private const val PAYLOAD_VERSION = 3
    private val LEGACY_CATEGORY_IDS = setOf(
        CategoryEntity.SHOPPING_ID,
        CategoryEntity.WORK_ID,
        CategoryEntity.HOME_ID,
    )
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KDF_ITERATIONS = 210_000
    private const val MIN_KDF_ITERATIONS = 100_000
    private const val MAX_KDF_ITERATIONS = 1_000_000
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_SIZE_BITS = 256
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val MIN_FILE_SIZE = 4 + 4 + 4 + SALT_SIZE + IV_SIZE + 4 + GCM_TAG_BYTES
}
