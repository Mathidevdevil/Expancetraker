package com.spendwise.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.app.data.model.SyncStatus
import com.spendwise.app.data.model.TripEntity
import com.spendwise.app.data.repository.FinanceRepository
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassRow
import com.spendwise.app.ui.components.glass.GlassSecondaryButton
import com.spendwise.app.ui.theme.*
import com.spendwise.app.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class TripWithStats(
    val trip: TripEntity,
    val spent: Double,
    val perDayAverage: Double,
    val totalDays: Int,
    val percent: Float
)

data class TripsUiState(
    val trips: List<TripWithStats> = emptyList(),
    val currencySymbol: String = "₹"
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repository: FinanceRepository
) : ViewModel() {

    val uiState: StateFlow<TripsUiState> = combine(
        repository.allTrips,
        repository.transactionsWithDetails
    ) { trips, txs ->
        val tripStats = trips.map { trip ->
            val tripTxs = txs.filter { it.transaction.tripId == trip.id && it.transaction.type == "expense" }
            val spent = tripTxs.sumOf { it.transaction.amount }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val days = runCatching {
                val start = sdf.parse(trip.startDate)?.time ?: 0L
                val end = sdf.parse(trip.endDate)?.time ?: 0L
                val diff = kotlin.math.abs(end - start)
                kotlin.math.max(1, (diff / (1000 * 60 * 60 * 24)).toInt() + 1)
            }.getOrDefault(1)

            val perDay = spent / days
            val pct = if (trip.budget > 0) ((spent / trip.budget) * 100).toFloat() else 0f

            TripWithStats(
                trip = trip,
                spent = spent,
                perDayAverage = perDay,
                totalDays = days,
                percent = pct
            )
        }

        TripsUiState(
            trips = tripStats,
            currencySymbol = "₹"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsUiState())

    fun createTrip(destination: String, startDate: String, endDate: String, budget: Double, notes: String?) {
        viewModelScope.launch {
            val trip = TripEntity(
                id = UUID.randomUUID().toString(),
                userId = "default-user",
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                budget = budget,
                notes = notes,
                syncStatus = SyncStatus.PENDING_INSERT
            )
            repository.insertTrip(trip)
        }
    }

    fun deleteTrip(id: String) {
        viewModelScope.launch {
            repository.deleteTrip(id)
        }
    }
}

@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val state by viewModel.uiState.collectAsState()
    var isAddTripOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Travel Plans",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) TextPrimaryDark else TextPrimaryLight
                    )
                    Text(
                        text = "Trip budgets, itinerary notes, and daily average spend",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextMutedDark else TextMutedLight
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                GlassSecondaryButton(
                    text = "New Trip",
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = TravelCyan
                        )
                    },
                    isDark = isDark,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isAddTripOpen = true
                    }
                )
            }
        }

        if (state.trips.isEmpty()) {
            item {
                GlassRow(
                    isDark = isDark,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FlightTakeoff,
                                contentDescription = null,
                                tint = TravelCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No travel plans configured yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                        }
                    }
                }
            }
        } else {
            items(state.trips, key = { it.trip.id }) { item ->
                val trip = item.trip
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    isDark = isDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = TravelCyan.copy(alpha = if (isDark) 0.22f else 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FlightTakeoff,
                                        contentDescription = null,
                                        tint = TravelCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = trip.destination,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) TextPrimaryDark else TextPrimaryLight
                                )
                                Text(
                                    text = "${CurrencyFormatter.formatTripDateRange(trip.startDate, trip.endDate)} (${item.totalDays} Days)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) TextMutedDark else TextMutedLight
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteTrip(trip.id)
                            },
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

                    if (!trip.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0x12FFFFFF) else Color(0x100F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = trip.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDark) TextMutedDark else TextMutedLight,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = TravelCyan,
                        trackColor = if (isDark) Color(0x1FFFFFFF) else Color(0x1F0F172A)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Spent / Budget",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                            Text(
                                text = "${CurrencyFormatter.format(item.spent, state.currencySymbol, false)} / ${CurrencyFormatter.format(trip.budget, state.currencySymbol, false)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDark) TextPrimaryDark else TextPrimaryLight
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Daily Average",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) TextMutedDark else TextMutedLight
                            )
                            Text(
                                text = "${CurrencyFormatter.format(item.perDayAverage, state.currencySymbol, false)}/day",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TravelCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (isAddTripOpen) {
        AddTripDialog(
            currencySymbol = state.currencySymbol,
            isDark = isDark,
            onDismiss = { isAddTripOpen = false },
            onSave = { dest, sDate, eDate, bgt, notes ->
                viewModel.createTrip(dest, sDate, eDate, bgt, notes)
                isAddTripOpen = false
            }
        )
    }
}

@Composable
fun AddTripDialog(
    currencySymbol: String,
    isDark: Boolean = isSystemInDarkTheme(),
    onDismiss: () -> Unit,
    onSave: (destination: String, startDate: String, endDate: String, budget: Double, notes: String?) -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var budgetText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (isDark) Color(0x1AFFFFFF) else Color(0x73FFFFFF),
        unfocusedContainerColor = if (isDark) Color(0x0FFFFFFF) else Color(0x52FFFFFF),
        focusedBorderColor = TravelCyan,
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
                "New Travel Plan",
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else TextPrimaryLight
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination (e.g. Goa, Tokyo)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Start (YYYY-MM-DD)") },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("End (YYYY-MM-DD)") },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Total Trip Budget ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Itinerary Notes (Optional)") },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            GlassGradientButton(
                text = "Create Plan",
                onClick = {
                    val bgt = budgetText.toDoubleOrNull() ?: 0.0
                    if (destination.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank()) {
                        onSave(destination, startDate, endDate, bgt, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = destination.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank(),
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
