package com.spendwise.app.ui.components

import android.app.DatePickerDialog
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.data.model.TransactionEntity
import com.spendwise.app.data.model.TripEntity
import com.spendwise.app.ui.categories.AddCategoryDialog
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassSegmentedControl
import com.spendwise.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    categories: List<CategoryEntity>,
    trips: List<TripEntity> = emptyList(),
    currencySymbol: String = "₹",
    isDark: Boolean = isSystemInDarkTheme(),
    initialTransaction: TransactionEntity? = null,
    onAddCategory: ((name: String, type: String, icon: String, color: String) -> Unit)? = null,
    onSave: (amount: Double, categoryId: String?, tripId: String?, paymentMethod: String, note: String?, date: String, type: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    val todayStr = remember { dateFormatter.format(Date()) }
    val yesterdayStr = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        dateFormatter.format(cal.time)
    }

    var isIncome by remember {
        mutableStateOf(initialTransaction?.type == "income")
    }

    var amountText by remember {
        val initialAmt = initialTransaction?.amount ?: 0.0
        val text = if (initialAmt > 0) {
            if (initialAmt % 1.0 == 0.0) initialAmt.toLong().toString() else initialAmt.toString()
        } else ""
        mutableStateOf(text)
    }

    var noteText by remember {
        mutableStateOf(initialTransaction?.note ?: "")
    }

    var selectedDate by remember {
        mutableStateOf(initialTransaction?.date ?: todayStr)
    }

    val filteredCategories = remember(categories, isIncome) {
        val targetType = if (isIncome) "income" else "expense"
        categories.filter { it.type == targetType }
    }

    var selectedCategoryId by remember(filteredCategories) {
        val initialCatId = initialTransaction?.categoryId
        val defaultId: String? = if (initialCatId != null && filteredCategories.any { it.id == initialCatId }) {
            initialCatId
        } else {
            filteredCategories.firstOrNull()?.id
        }
        mutableStateOf<String?>(defaultId)
    }

    var selectedTripId by remember {
        mutableStateOf<String?>(initialTransaction?.tripId)
    }

    var selectedPaymentMethod by remember {
        mutableStateOf(initialTransaction?.paymentMethod ?: "upi")
    }

    var isAddCategoryDialogOpen by remember { mutableStateOf(false) }

    val paymentMethods = listOf(
        "upi" to "UPI",
        "card" to "Card",
        "cash" to "Cash",
        "bank_transfer" to "Bank"
    )

    val formattedDateDisplay = remember(selectedDate) {
        try {
            val parsed = dateFormatter.parse(selectedDate)
            if (parsed != null) displayDateFormatter.format(parsed) else selectedDate
        } catch (e: Exception) {
            selectedDate
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xF2121524) else Color(0xF7F8FAFC),
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
                    text = if (initialTransaction != null) {
                        if (isIncome) "Edit Income" else "Edit Expense"
                    } else {
                        if (isIncome) "Add Income" else "Add Expense"
                    },
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

            // Amount Input Card with sleek 1dp container border
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
                                amountText = (current + value).toLong().toString()
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

            // Category Section with (+) Add Category Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) TextMutedDark else TextMutedLight
                )
                Text(
                    text = "${filteredCategories.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextTertiaryDark else TextTertiaryLight
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Inline (+) Add New Category chip
                item {
                    val addChipBorder = if (isDark) AppleIndigoLight.copy(alpha = 0.6f) else AppleIndigo.copy(alpha = 0.5f)
                    val addChipBg = if (isDark) AppleIndigo.copy(alpha = 0.2f) else AppleIndigo.copy(alpha = 0.08f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(addChipBg, RoundedCornerShape(16.dp))
                            .border(1.dp, addChipBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isAddCategoryDialogOpen = true
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Category",
                                tint = if (isDark) AppleIndigoLight else AppleIndigo,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "New",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) AppleIndigoLight else AppleIndigo
                            )
                        }
                    }
                }

                // Existing Category Chips
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
                        if (isDark) Color(0x33FFFFFF) else Color(0x2E0F172A)
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

            // Date Selection (Today, Yesterday, Custom Date with DatePickerDialog) & Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Column
                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Date",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Today Chip
                        val isToday = selectedDate == todayStr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isToday) (if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) GlassFillDark else GlassFillLight),
                                    RoundedCornerShape(11.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isToday) SolidColor(if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush),
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable {
                                    selectedDate = todayStr
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) Color.White else (if (isDark) TextPrimaryDark else TextPrimaryLight)
                            )
                        }

                        // Yesterday Chip
                        val isYday = selectedDate == yesterdayStr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isYday) (if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) GlassFillDark else GlassFillLight),
                                    RoundedCornerShape(11.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isYday) SolidColor(if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush),
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable {
                                    selectedDate = yesterdayStr
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Y'day",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isYday) Color.White else (if (isDark) TextPrimaryDark else TextPrimaryLight)
                            )
                        }

                        // Custom Date Picker Chip
                        val isCustomDate = !isToday && !isYday
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (isCustomDate) (if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) GlassFillDark else GlassFillLight),
                                    RoundedCornerShape(11.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isCustomDate) SolidColor(if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush),
                                    RoundedCornerShape(11.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val currentCal = Calendar.getInstance()
                                    try {
                                        val d = dateFormatter.parse(selectedDate)
                                        if (d != null) currentCal.time = d
                                    } catch (e: Exception) {}

                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val c = Calendar.getInstance()
                                            c.set(y, m, d)
                                            selectedDate = dateFormatter.format(c.time)
                                        },
                                        currentCal.get(Calendar.YEAR),
                                        currentCal.get(Calendar.MONTH),
                                        currentCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "Pick Date",
                                    tint = if (isCustomDate) Color.White else (if (isDark) AppleIndigoLight else AppleIndigo),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isCustomDate) formattedDateDisplay else "Edit Date",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCustomDate) Color.White else (if (isDark) TextPrimaryDark else TextPrimaryLight),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Payment Method Column
                Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Payment Method",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                    .border(
                                        1.dp,
                                        if (isSelected) SolidColor(if (isDark) AppleIndigoLight else AppleIndigo) else (if (isDark) DarkGlassBorderBrush else LightGlassBorderBrush),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedPaymentMethod = key
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                            }
                        }
                    }
                }
            }

            // Note Input with defined 1dp border
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = {
                    Text(
                        "Note (e.g. Starbucks, Groceries, Client Dinner)",
                        color = if (isDark) TextTertiaryDark else TextTertiaryLight
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x52FFFFFF),
                    unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x33FFFFFF),
                    focusedBorderColor = if (isDark) AppleIndigoLight else AppleIndigo,
                    unfocusedBorderColor = if (isDark) Color(0x33FFFFFF) else Color(0x2E0F172A),
                    focusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight,
                    unfocusedTextColor = if (isDark) TextPrimaryDark else TextPrimaryLight
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Submit Button
            val canSave = (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            GlassGradientButton(
                text = if (initialTransaction != null) {
                    "Save Changes"
                } else {
                    if (isIncome) "Save Income" else "Save Expense"
                },
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
                            selectedDate,
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

    // Modal to add a new category on the fly
    if (isAddCategoryDialogOpen) {
        AddCategoryDialog(
            type = if (isIncome) "income" else "expense",
            isDark = isDark,
            onDismiss = { isAddCategoryDialogOpen = false },
            onSave = { name, icon, color ->
                onAddCategory?.invoke(name, if (isIncome) "income" else "expense", icon, color)
                isAddCategoryDialogOpen = false
            }
        )
    }
}
