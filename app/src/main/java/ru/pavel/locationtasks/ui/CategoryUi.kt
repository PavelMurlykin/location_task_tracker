package ru.pavel.locationtasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.CategoryEntity

@Composable
internal fun CategoryEntity.localizedName(): String = name.ifBlank {
    stringResource(
        when (id) {
            CategoryEntity.SHOPPING_ID -> R.string.category_shopping
            CategoryEntity.WORK_ID -> R.string.category_work
            CategoryEntity.HOME_ID -> R.string.category_home
            else -> R.string.category_unknown
        },
    )
}

internal fun CategoryEntity.color(): Color = Color(colorArgb)

internal fun contentColorForCategory(background: Color): Color =
    if (background.luminance() < 0.45f) Color.White else Color(0xFF191919)
