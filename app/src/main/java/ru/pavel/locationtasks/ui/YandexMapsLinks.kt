package ru.pavel.locationtasks.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.pavel.locationtasks.R

@Composable
fun YandexMapsTermsLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(YANDEX_MAPS_TERMS_URL) },
        modifier = modifier,
    ) {
        Text(stringResource(R.string.yandex_maps_terms))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
    }
}

@Composable
fun OpenInYandexMapsButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
    ) {
        TextButton(onClick = { uriHandler.openUri(YANDEX_MAPS_URL) }) {
            Text(stringResource(R.string.open_in_yandex_maps))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
        }
    }
}

private const val YANDEX_MAPS_URL = "https://yandex.ru/maps/"
private const val YANDEX_MAPS_TERMS_URL = "https://yandex.ru/legal/maps_termsofuse/"
