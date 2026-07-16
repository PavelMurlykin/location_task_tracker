package ru.pavel.locationtasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Конфиденциальность") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PolicyHeading("Какие данные используются")
            Text(
                "Задачи, выбранные координаты и настройки хранятся локально на вашем устройстве. В текущей версии нет учётных записей и собственного сервера.",
            )
            PolicyHeading("Местоположение")
            Text(
                "С вашего разрешения приложение использует точное местоположение, в том числе в фоне, когда приложение закрыто или не используется. Это необходимо, чтобы Android определил вход в место задачи и показал напоминание. История перемещений не записывается.",
            )
            PolicyHeading("Управление данными")
            Text(
                "Геонапоминания можно отключать отдельно для каждой задачи. Разрешения можно отозвать в системных настройках. Удаление данных приложения удалит локальные задачи и настройки.",
            )
            PolicyHeading("Карты")
            Text(
                "При настроенном API-ключе экран выбора места использует Google Maps SDK for Android, работа которого регулируется политикой конфиденциальности Google.",
            )
            Text(
                "Обновлено 15 июля 2026 года",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PolicyHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
