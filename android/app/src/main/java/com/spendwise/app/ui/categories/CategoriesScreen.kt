package com.spendwise.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.data.model.SyncStatus
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.ui.components.CategoryIconView
import com.spendwise.app.ui.components.getIconVector
import com.spendwise.app.ui.components.glass.GlassDeleteConfirmDialog
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassRow
import com.spendwise.app.ui.components.glass.GlassSecondaryButton
import com.spendwise.app.ui.components.glass.GlassSegmentedControl
import com.spendwise.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, type: String, icon: String, color: String) {
        viewModelScope.launch {
            val cat = CategoryEntity(
                name = name,
                type = type,
                icon = icon,
                color = color,
                isDefault = false,
                syncStatus = SyncStatus.PENDING_INSERT
            )
            repository.insertCategory(cat)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.deleteCategory(id)
        }
    }
}

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val categories by viewModel.categories.collectAsState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    val selectedTab = if (selectedIndex == 0) "expense" else "income"
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val haptic = LocalHapticFeedback.current

    val expenseCount = categories.count { it.type == "expense" }
    val incomeCount = categories.count { it.type == "income" }
    val tabLabels = listOf("Expenses ($expenseCount)", "Incomes ($incomeCount)")

    val filtered = remember(categories, selectedTab) {
        categories.filter { it.type == selectedTab }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Manage tags, icons, and colors",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            GlassSecondaryButton(
                text = "New",
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isDark) AppleIndigoLight else AppleIndigo
                    )
                },
                isDark = isDark,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isAddDialogOpen = true
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Apple Liquid Glass Segmented Lens Slider
        GlassSegmentedControl(
            items = tabLabels,
            selectedIndex = selectedIndex,
            onSelect = { selectedIndex = it },
            isDark = isDark,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Categories List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { cat ->
                GlassRow(
                    isDark = isDark,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CategoryIconView(
                            iconName = cat.icon,
                            colorHex = cat.color,
                            size = 42.dp
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                            Text(
                                text = if (cat.isDefault) "Default System Category" else "Custom Category",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                        }

                        if (!cat.isDefault) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    pendingDeleteCategory = cat
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteCategory?.let { cat ->
        GlassDeleteConfirmDialog(
            title = "Delete Category?",
            message = "Are you sure you want to delete the category \"${cat.name}\"? Any transactions assigned to this category will become uncategorized.",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                viewModel.deleteCategory(cat.id)
                pendingDeleteCategory = null
            },
            onDismiss = {
                pendingDeleteCategory = null
            },
            isDark = isDark
        )
    }

    if (isAddDialogOpen) {
        AddCategoryDialog(
            type = selectedTab,
            isDark = isDark,
            onDismiss = { isAddDialogOpen = false },
            onSave = { name, icon, color ->
                viewModel.addCategory(name, selectedTab, icon, color)
                isAddDialogOpen = false
            }
        )
    }
}

@Composable
fun AddCategoryDialog(
    type: String,
    isDark: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Tag") }
    var selectedColor by remember { mutableStateOf("#5B4BE8") }

    val icons = listOf("Tag", "Utensils", "ShoppingCart", "Car", "Film", "Home", "HeartPulse", "GraduationCap", "Gift", "Plane")
    val colors = listOf("#5B4BE8", "#10B981", "#F43F5E", "#F59E0B", "#06B6D4", "#8B5CF6", "#EC4899", "#3B82F6")

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x73FFFFFF),
        unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x52FFFFFF),
        focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
        unfocusedBorderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A),
        focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
        unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = if (isDark) SolidFallbackDark else SolidFallbackLight,
        title = {
            Text(
                "New ${type.capitalize()} Category",
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Select Icon",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons) { iconName ->
                        val isSelected = selectedIcon == iconName
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) (if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) Color(0x1AFFFFFF) else Color(0x1F0F172A)),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { selectedIcon = iconName }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    getIconVector(iconName),
                                    contentDescription = iconName,
                                    tint = if (isSelected) Color.White else (if (isDark) TextPrimaryDark else TextPrimaryLight),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "Select Color",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(colors) { hex ->
                        val isSelected = selectedColor == hex
                        val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(AppleIndigo)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { selectedColor = hex }
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) (if (isDark) Color.White else Color(0xFF0F172A)) else Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            GlassGradientButton(
                text = "Save Category",
                onClick = {
                    if (name.isNotBlank()) onSave(name.trim(), selectedIcon, selectedColor)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }
        }
    )
}

fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}
