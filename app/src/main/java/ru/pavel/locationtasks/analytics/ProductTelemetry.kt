package ru.pavel.locationtasks.analytics

import android.content.Context
import com.posthog.PostHog
import com.posthog.PostHogConfig
import com.posthog.PostHogInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.BuildConfig
import ru.pavel.locationtasks.data.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

interface ProductTelemetry {
    fun start()
    fun trackOnboardingCompleted()
    fun trackGeofenceRegistration(outcome: String)
    fun trackGeofenceTrigger(outcome: String)
    fun trackNotificationAction(action: String, reminderKind: String)
    fun trackDueReminder(delivered: Boolean)
    fun captureException(throwable: Throwable, operation: String)

    companion object {
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_NOTIFIED = "notified"
        const val ACTION_COMPLETE = "complete"
    }
}

@Singleton
class ConsentAwareProductTelemetry @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val metricsRepository: ProductMetricsRepository,
) : ProductTelemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var consentGranted = false
    @Volatile private var started = false
    @Volatile private var postHog: PostHogInterface? = null

    override fun start() {
        if (started) return
        started = true
        scope.launch {
            preferencesRepository.productPreferences
                .map { it.analyticsConsent }
                .distinctUntilChanged()
                .collect(::applyConsent)
        }
    }

    override fun trackOnboardingCompleted() {
        scope.launch {
            if (!ensureConsent()) return@launch
            capture(EVENT_ONBOARDING_COMPLETED)
        }
    }

    override fun trackGeofenceRegistration(outcome: String) {
        scope.launch {
            if (!ensureConsent()) return@launch
            metricsRepository.recordGeofenceRegistration(outcome == ProductTelemetry.OUTCOME_SUCCESS)
            capture(EVENT_GEOFENCE_REGISTRATION, mapOf(PROPERTY_OUTCOME to outcome))
        }
    }

    override fun trackGeofenceTrigger(outcome: String) {
        scope.launch {
            if (!ensureConsent()) return@launch
            metricsRepository.recordGeofenceTrigger(outcome == ProductTelemetry.OUTCOME_NOTIFIED)
            capture(EVENT_GEOFENCE_TRIGGER, mapOf(PROPERTY_OUTCOME to outcome))
        }
    }

    override fun trackNotificationAction(action: String, reminderKind: String) {
        scope.launch {
            if (!ensureConsent()) return@launch
            if (action == ProductTelemetry.ACTION_COMPLETE) {
                metricsRepository.recordNotificationCompletion()
            }
            capture(
                EVENT_NOTIFICATION_ACTION,
                mapOf(PROPERTY_ACTION to action, PROPERTY_REMINDER_KIND to reminderKind),
            )
        }
    }

    override fun trackDueReminder(delivered: Boolean) {
        scope.launch {
            if (!ensureConsent()) return@launch
            metricsRepository.recordDueReminderDelivery(delivered)
            capture(
                EVENT_REMINDER_DELIVERY,
                mapOf(
                    PROPERTY_REMINDER_KIND to "due",
                    PROPERTY_OUTCOME to if (delivered) {
                        ProductTelemetry.OUTCOME_NOTIFIED
                    } else {
                        "notifications_blocked"
                    },
                ),
            )
        }
    }

    override fun captureException(throwable: Throwable, operation: String) {
        scope.launch {
            if (!ensureConsent() || !BuildConfig.SENTRY_CONFIGURED) return@launch
            Sentry.withScope { sentryScope ->
                sentryScope.setTag(PROPERTY_OPERATION, operation)
                Sentry.captureException(throwable)
            }
        }
    }

    private suspend fun ensureConsent(): Boolean {
        if (!preferencesRepository.productPreferences.first().analyticsConsent) return false
        consentGranted = true
        initializeProviders()
        return true
    }

    private suspend fun applyConsent(consent: Boolean) {
        consentGranted = consent
        if (!consent) {
            postHog?.reset()
            postHog?.close()
            postHog = null
            if (Sentry.isEnabled()) Sentry.close()
            metricsRepository.clear()
            return
        }
        initializeProviders()
        val daysSinceFirstOpen = metricsRepository.recordAppOpen()
        capture(EVENT_APP_OPEN, mapOf(PROPERTY_DAYS_SINCE_FIRST_OPEN to daysSinceFirstOpen))
    }

    @Synchronized
    private fun initializeProviders() {
        if (BuildConfig.SENTRY_CONFIGURED && !Sentry.isEnabled()) {
            SentryAndroid.init(context) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
                options.isSendDefaultPii = false
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.tracesSampleRate = 0.0
                options.isEnableAutoSessionTracking = true
            }
        }
        if (BuildConfig.POSTHOG_CONFIGURED && postHog == null) {
            postHog = PostHog.with(
                PostHogConfig(BuildConfig.POSTHOG_API_KEY, BuildConfig.POSTHOG_HOST),
            )
        }
    }

    private fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        if (!consentGranted) return
        postHog?.capture(event = event, properties = properties)
    }

    companion object {
        private const val EVENT_APP_OPEN = "app_open"
        private const val EVENT_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val EVENT_GEOFENCE_REGISTRATION = "geofence_registration"
        private const val EVENT_GEOFENCE_TRIGGER = "geofence_trigger"
        private const val EVENT_NOTIFICATION_ACTION = "notification_action"
        private const val EVENT_REMINDER_DELIVERY = "reminder_delivery"
        private const val PROPERTY_OUTCOME = "outcome"
        private const val PROPERTY_ACTION = "action"
        private const val PROPERTY_REMINDER_KIND = "reminder_kind"
        private const val PROPERTY_OPERATION = "operation"
        private const val PROPERTY_DAYS_SINCE_FIRST_OPEN = "days_since_first_open"
    }
}
