package ru.pavel.locationtasks.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.CATEGORY_COLOR_PALETTE
import ru.pavel.locationtasks.data.CategoryEntity
import ru.pavel.locationtasks.data.CategoryRepository
import ru.pavel.locationtasks.data.CategorySaveResult
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun save(
        categoryId: String?,
        name: String,
        colorArgb: Int,
        onResult: (CategorySaveResult) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(repository.save(categoryId, name, colorArgb))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onClose: () -> Unit,
    viewModel: CategoryManagementViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editedCategory = null
                    showEditor = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_add))
            }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.categories_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.categories_description),
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(categories, key = CategoryEntity::id) { category ->
                    Card(
                        onClick = {
                            editedCategory = category
                            showEditor = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .background(category.color(), CircleShape),
                            )
                            Text(
                                category.localizedName(),
                                modifier = Modifier.padding(start = 14.dp).weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val fallbackName = editedCategory?.localizedName().orEmpty()
        val existingNames = categories.associate { it.id to it.localizedName() }
        CategoryEditorDialog(
            category = editedCategory,
            initialName = fallbackName,
            existingNames = existingNames,
            onDismiss = { showEditor = false },
            onSave = { name, color, onResult ->
                viewModel.save(editedCategory?.id, name, color, onResult)
            },
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    category: CategoryEntity?,
    initialName: String,
    existingNames: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (String, Int, (CategorySaveResult) -> Unit) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(initialName) }
    var selectedColor by remember(category?.id) {
        mutableIntStateOf(category?.colorArgb ?: CATEGORY_COLOR_PALETTE.first())
    }
    var errorRes by remember(category?.id) { mutableStateOf<Int?>(null) }
    var isSaving by remember(category?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (category == null) R.string.category_add else R.string.category_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorRes = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.category_name)) },
                    supportingText = errorRes?.let { message ->
                        { Text(stringResource(message)) }
                    },
                    isError = errorRes != null,
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.category_color),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CATEGORY_COLOR_PALETTE.forEachIndexed { index, colorArgb ->
                        val color = Color(colorArgb)
                        val selected = colorArgb == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { selectedColor = colorArgb },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(
                                    if (selected) {
                                        R.string.category_color_selected
                                    } else {
                                        R.string.category_color_option
                                    },
                                    index + 1,
                                ),
                                tint = if (selected) {
                                    contentColorForCategory(color)
                                } else {
                                    Color.Transparent
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedName = name.trim()
                    val duplicatesAnotherCategory = existingNames.any { (id, existingName) ->
                        id != category?.id && existingName.equals(normalizedName, ignoreCase = true)
                    }
                    if (duplicatesAnotherCategory) {
                        errorRes = R.string.category_error_duplicate
                        return@TextButton
                    }
                    isSaving = true
                    onSave(name, selectedColor) { result ->
                        isSaving = false
                        errorRes = result.errorMessageRes()
                        if (result == CategorySaveResult.SAVED) onDismiss()
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@StringRes
private fun CategorySaveResult.errorMessageRes(): Int? = when (this) {
    CategorySaveResult.SAVED -> null
    CategorySaveResult.EMPTY_NAME -> R.string.category_error_empty
    CategorySaveResult.NAME_TOO_LONG -> R.string.category_error_too_long
    CategorySaveResult.DUPLICATE_NAME -> R.string.category_error_duplicate
}
