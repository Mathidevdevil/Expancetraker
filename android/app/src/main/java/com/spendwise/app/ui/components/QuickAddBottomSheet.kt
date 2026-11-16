package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.data.model.TripEntity
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassSegmentedControl
import com.spendwise.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    categories: List<CategoryEntity>,
    trips: List<TripEntity> = emptyList(),
    currencySymbol: String = "₹",
    isDark: Boolean = isSystemInDarkTheme(),
    onSave: (amount: Double, categoryId: String?, tripId: String?, paymentMethod: String, note: String?, type: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var isIncome by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    val filteredCategories = remember(categories, isIncome) {
        val targetType = if (isIncome) "income" else "expense"
        categories.filter { it.type == targetType }
    }

    var selectedCategoryId by remember(filteredCategories) {
        mutableStateOf(filteredCategories.firstOrNull()?.id)
    }
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf("upi") }
    var selectedDateQuick by remember { mutableStateOf("today") }

    val paymentMethods = listOf(
        "upi" to "UPI",
        "card" to "Card",
        "cash" to "Cash",
        "bank_transfer" to "Bank"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xF2121524) else Color(0xF7F4F4FA),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Color(0x66FFFFFF) else Color(0x4D0F172A)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isIncome) "Add Income" else "Add Expense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = if (isDark) TextMutedDark else TextMutedLight
                    )
                }
            }

            // Type Toggle: Liquid Glass Segmented Control
            GlassSegmentedControl(
                items = listOf("Expense", "Income"),
                selectedIndex = if (isIncome) 1 else 0,
                onSelect = { index ->
                    isIncome = index == 1
                },
                isDark = isDark
            )

            // Amount Input Card
            GlassCard(
                isDark = isDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currencySymbol,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isIncome) IncomeEmerald else ExpenseRose
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = {
                            Text(
                                "0",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) TextTertiaryDark else TextTertiaryLight
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                            unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Quick Amount Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(100, 200, 500, 1000).forEach { value ->
                    val fill = if (isDark) GlassFillDark else GlassFillLight
                    val border = if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(fill, RoundedCornerShape(14.dp))
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .clickable {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                amountText = (current + value).toInt().toString()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$currencySymbol$value",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) TextPrimaryDark else TextPrimaryLight
                        )
                    }
                }
            }

            // Category Chips Carousel
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) TextMutedDark else TextMutedLight
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredCategories, key = { it.id }) { cat ->
                    val isSelected = cat.id == selectedCategoryId
                    val chipFill = if (isSelected) {
                        if (isDark) AppleIndigo.copy(alpha = 0.35f) else AppleIndigo.copy(alpha = 0.15f)
                    } else {
                        if (isDark) GlassFillDark else GlassFillLight
                    }
                    val chipBorder = if (isSelected) {
                        if (isDark) AppleIndigoLight else AppleIndigo
                    } else {
                        Color(0x1FFFFFFF)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipFill, RoundedCornerShape(16.dp))
                            .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                selectedCategoryId = cat.id
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryIconView(
                                iconName = cat.icon,
                                colorHex = cat.color,
                                size = 26.dp
                            )
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    if (isDark) AppleIndigoLight else AppleIndigo
                                } else {
                                    if (isDark) TextPrimaryDark else TextPrimaryLight
                                }
                            )
                        }
                    }
                }
            }

            // Date Quick Picker & Payment Method Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Chips
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("today" to "Today", "yesterday" to "Y'day").forEach { (key, label) ->
                            val isSelected = selectedDateQuick == key
                            val fill = if (isSelected) {
                                if (isDark) AppleIndigoLight else AppleIndigo
                            } else {
                                if (isDark) GlassFillDark else GlassFillLight
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(fill, RoundedCornerShape(12.dp))
                                    .then(
                                        if (!isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush,
                                                RoundedCornerShape(12.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedDateQuick = key }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                            }
                        }
                    }
                }

                // Payment Method
                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Payment Method",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        paymentMethods.forEach { (key, label) ->
                            val isSelected = selectedPaymentMethod == key
                            val fill = if (isSelected) {
                                if (isDark) AppleIndigoLight else AppleIndigo
                            } else {
                                if (isDark) GlassFillDark else GlassFillLight
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(fill, RoundedCornerShape(10.dp))
                                    .then(
                                        if (!isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush,
                                                RoundedCornerShape(10.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedPaymentMethod = key }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                            }
                        }
                    }
                }
            }

            // Note Input
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = {
                    Text(
                        "Note (e.g. Starbucks, Groceries)",
                        color = if (isDark) TextTertiaryDark else TextTertiaryLight
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x52FFFFFF),
                    unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x33FFFFFF),
                    focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
                    unfocusedBorderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A),
                    focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                    unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Submit Button
            val canSave = (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            GlassGradientButton(
                text = if (isIncome) "Save Income" else "Save Expense",
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(
                            amount,
                            selectedCategoryId,
                            selectedTripId,
                            selectedPaymentMethod,
                            noteText.takeIf { it.isNotBlank() },
                            if (isIncome) "income" else "expense"
                        )
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
