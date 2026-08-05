package ru.pavel.locationtasks.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ru.pavel.locationtasks.R

fun canUseDeviceAuthentication(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(DEVICE_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun requestDeviceAuthentication(
    activity: FragmentActivity,
    onAuthenticated: () -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                onAuthenticated()
            }
        },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.device_unlock_title))
        .setSubtitle(activity.getString(R.string.device_unlock_description))
        .setAllowedAuthenticators(DEVICE_AUTHENTICATORS)
        .build()
    prompt.authenticate(promptInfo)
}

private const val DEVICE_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
