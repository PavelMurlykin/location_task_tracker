package ru.pavel.locationtasks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.location.LocationPermissionState

@Composable
fun OnboardingScreen(onComplete: (analyticsConsent: Boolean) -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var permissionStep by rememberSaveable { mutableIntStateOf(0) }
    var analyticsConsent by rememberSaveable { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }

    fun refreshPermissions() {
        permissions = LocationPermissionState.from(context)
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissions()
        permissionStep = 1
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissions()
        permissionStep = 2
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshPermissions()
        permissionStep = 2
    }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissions()
        permissionStep = 3
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_progress, page + 1, 3),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                when (page) {
                    0 -> OnboardingExplanation()
                    1 -> OnboardingPermissions(
                        permissions = permissions,
                        permissionStep = permissionStep,
                    )
                    else -> OnboardingPrivacy(
                        consent = analyticsConsent,
                        onConsentChange = { analyticsConsent = it },
                    )
                }
            }
            Button(
                onClick = {
                    when (page) {
                        0 -> page = 1
                        1 -> when (permissionStep) {
                            0 -> if (permissions.preciseLocation) {
                                permissionStep = 1
                            } else {
                                foregroundLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                            1 -> when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                                    permissionStep = 2
                                }
                                permissions.backgroundLocation -> permissionStep = 2
                                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                                    backgroundLauncher.launch(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                    )
                                }
                                else -> settingsLauncher.launch(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    },
                                )
                            }
                            2 -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                permissions.notifications
                            ) {
                                permissionStep = 3
                            } else {
                                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            else -> page = 2
                        }
                        else -> onComplete(analyticsConsent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            page == 0 -> R.string.common_next
                            page == 1 && permissionStep == 0 ->
                                R.string.onboarding_allow_location
                            page == 1 && permissionStep == 1 ->
                                R.string.onboarding_allow_background
                            page == 1 && permissionStep == 2 ->
                                R.string.onboarding_allow_notifications
                            page == 1 -> R.string.common_next
                            else -> R.string.onboarding_start
                        },
                    ),
                )
            }
            if (page == 1 && permissionStep < 3) {
                TextButton(
                    onClick = { page = 2 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_continue_without_permissions))
                }
            }
        }
    }
}

@Composable
private fun OnboardingExplanation() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            Icons.Default.Map,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.onboarding_welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.onboarding_geofence_explanation),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun OnboardingPermissions(
    permissions: LocationPermissionState,
    permissionStep: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.onboarding_permissions_text),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_precise_location),
            granted = permissions.preciseLocation,
            active = permissionStep == 0,
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_background_location),
            granted = permissions.backgroundLocation,
            active = permissionStep == 1,
        )
        PermissionStatusRow(
            label = stringResource(R.string.permission_notifications),
            granted = permissions.notifications,
            active = permissionStep == 2,
        )
        Text(
            stringResource(R.string.onboarding_permissions_can_change),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean, active: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (label == stringResource(R.string.permission_notifications)) {
                    Icons.Default.Notifications
                } else {
                    Icons.Default.LocationOn
                },
                contentDescription = null,
                tint = if (granted || active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(label, modifier = Modifier.weight(1f))
            Text(
                stringResource(
                    if (granted) R.string.status_allowed else R.string.status_not_allowed,
                ),
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun OnboardingPrivacy(consent: Boolean, onConsentChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            Icons.Default.PrivacyTip,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.onboarding_privacy_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.onboarding_privacy_text),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.analytics_consent_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.analytics_consent_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = consent, onCheckedChange = onConsentChange)
            }
        }
        Text(
            stringResource(R.string.analytics_no_personal_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
