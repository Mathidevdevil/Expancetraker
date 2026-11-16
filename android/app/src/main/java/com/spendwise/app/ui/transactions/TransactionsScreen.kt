package com.spendwise.app.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.data.model.TransactionWithDetails
import com.spendwise.app.ui.components.CategoryIconView
import com.spendwise.app.ui.components.glass.GlassIconButton
import com.spendwise.app.ui.components.glass.GlassRow
import com.spendwise.app.ui.theme.*
import com.spendwise.app.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onOpenQuickAdd: () -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Group filtered transactions by friendly date
    val groupedTransactions = remember(state.transactions) {
        state.transactions.groupBy { CurrencyFormatter.formatFriendlyDate(it.transaction.date) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )
                Text(
                    text = "${state.transactions.size} transactions recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
            }

            // Quick Add glass icon button
            GlassIconButton(
                onClick = onOpenQuickAdd,
                size = 42.dp,
                isDark = isDark
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    tint = if (isDark) AppleIndigoLight else AppleIndigo
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Apple Liquid Glass Search Bar
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = {
                Text(
                    "Search notes, categories, trips...",
                    color = if (isDark) TextTertiaryDark else TextTertiaryLight
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isDark) TextMutedDark else TextMutedLight
                )
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = if (isDark) TextMutedDark else TextMutedLight
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x73FFFFFF),
                unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x52FFFFFF),
                focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
                unfocusedBorderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A),
                focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Glass Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                GlassFilterChip(
                    label = "All",
                    isSelected = state.filterType == "all",
                    selectedColor = if (isDark) AppleIndigoLight else AppleIndigo,
                    isDark = isDark,
                    onClick = {
                        viewModel.onFilterTypeChanged("all")
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            item {
                GlassFilterChip(
                    label = "Expenses",
                    isSelected = state.filterType == "expense",
                    selectedColor = ExpenseRose,
                    isDark = isDark,
                    onClick = {
                        viewModel.onFilterTypeChanged("expense")
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            item {
                GlassFilterChip(
                    label = "Incomes",
                    isSelected = state.filterType == "income",
                    selectedColor = IncomeEmerald,
                    isDark = isDark,
                    onClick = {
                        viewModel.onFilterTypeChanged("income")
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            items(state.categories, key = { it.id }) { cat ->
                val isSelected = state.filterCategoryId == cat.id
                GlassFilterChip(
                    label = cat.name,
                    isSelected = isSelected,
                    selectedColor = if (isDark) AppleIndigoLight else AppleIndigo,
                    isDark = isDark,
                    onClick = {
                        val newId = if (isSelected) null else cat.id
                        viewModel.onFilterCategoryChanged(newId)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transactions List with Date Grouping & Swipe to Delete
        if (state.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0x1AFFFFFF) else Color(0x2E0F172A),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = if (isDark) TextMutedDark else TextMutedLight,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = "No matching transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )
                    Text(
                        text = "Try adjusting your filters or search term",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedTransactions.forEach { (dateHeader, txList) ->
                    item {
                        Text(
                            text = dateHeader.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = if (isDark) TextMutedDark else TextMutedLight,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(txList, key = { it.transaction.id }) { item ->
                        TransactionRowItem(
                            item = item,
                            currencySymbol = state.currencySymbol,
                            isDark = isDark,
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteTransaction(item.transaction.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassFilterChip(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val fill = if (isSelected) {
        selectedColor
    } else {
        if (isDark) GlassFillDark else GlassFillLight
    }
    val borderBrush = if (isSelected) {
        if (isDark) DarkGlassLensBorderBrush else LightGlassLensBorderBrush
    } else {
        if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fill, RoundedCornerShape(12.dp))
            .border(1.dp, borderBrush, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else if (isDark) TextPrimaryDark else TextPrimaryLight
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionRowItem(
    item: TransactionWithDetails,
    currencySymbol: String,
    isDark: Boolean,
    onDelete: () -> Unit
) {
    val isIncome = item.transaction.type == "income"
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    ExpenseRose
                } else {
                    if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A)
                },
                label = "swipeBg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    ) {
        GlassRow(
            isDark = isDark,
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
                    iconName = item.category?.icon ?: "Tag",
                    colorHex = item.category?.color ?: "#5B4BE8",
                    size = 42.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.transaction.note?.takeIf { it.isNotBlank() } ?: (item.category?.name ?: "Transaction"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.category?.name ?: "General"} • ${item.transaction.paymentMethod}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextMutedDark else TextMutedLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isIncome) {
                            CurrencyFormatter.formatWithSign(item.transaction.amount, currencySymbol)
                        } else {
                            "-${CurrencyFormatter.format(item.transaction.amount, currencySymbol)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) IncomeEmerald else if (isDark) TextPrimaryDark else TextPrimaryLight,
                        maxLines = 1
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = if (isDark) TextMutedDark.copy(alpha = 0.5f) else TextMutedLight.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
