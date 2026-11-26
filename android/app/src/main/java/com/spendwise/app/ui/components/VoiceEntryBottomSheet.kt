package com.spendwise.app.ui.components

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.spendwise.app.data.model.CategoryEntity
import com.spendwise.app.ui.components.glass.GlassCard
import com.spendwise.app.ui.components.glass.GlassGradientButton
import com.spendwise.app.ui.components.glass.GlassSecondaryButton
import com.spendwise.app.ui.theme.*
import com.spendwise.app.voice.ParsedVoiceTransaction
import com.spendwise.app.voice.VoiceSpeechError
import com.spendwise.app.voice.VoiceSpeechErrorType
import com.spendwise.app.voice.VoiceSpeechManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

private enum class VoiceSheetStage {
    LISTENING,
    PARSING,
    CONFIRMATION,
    ERROR
}

private fun formatIndianInr(amount: Double?): String {
    if (amount == null || amount <= 0.0) return "—"
    val integerPart = amount.toLong()
    val decimalPart = ((amount - integerPart) * 100).toInt()
    val intStr = integerPart.toString()
    val grouped = if (intStr.length <= 3) {
        intStr
    } else {
        val last3 = intStr.takeLast(3)
        val rest = intStr.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ",")
            sb.insert(0, rest.substring(start, i))
            i -= 2
        }
        "$sb,$last3"
    }
    return if (decimalPart > 0) {
        val dec = DecimalFormat("00", DecimalFormatSymbols(Locale.US)).format(decimalPart)
        "₹$grouped.$dec"
    } else {
        "₹$grouped"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEntryBottomSheet(
    onDismiss: () -> Unit,
    onSwitchToManual: () -> Unit,
    categories: List<CategoryEntity>,
    voiceLanguage: String,
    isDark: Boolean = isSystemInDarkTheme(),
    onParseTranscript: suspend (String) -> ParsedVoiceTransaction,
    onSaveTransaction: (
        amount: Double,
        categoryId: String?,
        paymentMethod: String,
        note: String?,
        date: String,
        type: String
    ) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val speechManager = remember(context) { VoiceSpeechManager(context) }
    val speechState by speechManager.state.collectAsState()

    var stage by remember { mutableStateOf(VoiceSheetStage.LISTENING) }
    var activeError by remember { mutableStateOf<VoiceSpeechError?>(null) }
    var triedSystemFallback by remember { mutableStateOf(false) }

    // Editable Confirmation Card states
    var selectedType by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var isEditingAmount by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isEditingCategory by remember { mutableStateOf(false) }
    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }
    var selectedPaymentMethod by remember { mutableStateOf("upi") }
    var noteText by remember { mutableStateOf("") }
    var isEditingNote by remember { mutableStateOf(false) }
    var rawTranscript by remember { mutableStateOf("") }
    var usedSmartParser by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val todayStr = remember { dateFormatter.format(Date()) }
    val yesterdayStr = remember {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.let {
            dateFormatter.format(it.time)
        }
    }

    fun applyParsedResult(transcript: String, parsed: ParsedVoiceTransaction) {
        rawTranscript = transcript
        selectedType = parsed.type
        amountText = if (parsed.amount != null && parsed.amount > 0.0) {
            if (parsed.amount % 1.0 == 0.0) parsed.amount.toLong().toString()
            else parsed.amount.toString()
        } else {
            ""
        }
        isEditingAmount = parsed.amount == null || parsed.amount <= 0.0
        selectedCategoryId = parsed.categoryId
        selectedDate = parsed.date
        selectedPaymentMethod = parsed.paymentMethod
        noteText = parsed.note
        usedSmartParser = parsed.usedSmartParser
        stage = VoiceSheetStage.CONFIRMATION
    }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()
            if (transcript.isNotBlank()) {
                stage = VoiceSheetStage.PARSING
                coroutineScope.launch {
                    val parsed = onParseTranscript(transcript)
                    applyParsedResult(transcript, parsed)
                }
                return@rememberLauncherForActivityResult
            }
        }
        activeError = VoiceSpeechError(
            type = VoiceSpeechErrorType.NO_SPEECH,
            message = "No speech captured. Try speaking again or enter manually."
        )
        stage = VoiceSheetStage.ERROR
    }

    fun launchSystemSpeechPrompt() {
        triedSystemFallback = true
        speechManager.cancelListening()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, voiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your expense or income")
        }
        runCatching {
            systemSpeechLauncher.launch(intent)
        }.onFailure {
            activeError = VoiceSpeechError(
                type = VoiceSpeechErrorType.UNAVAILABLE,
                message = "No Google or system speech app is installed on this device. Please enter manually."
            )
            stage = VoiceSheetStage.ERROR
        }
    }

    fun startVoiceRecognition() {
        activeError = null
        stage = VoiceSheetStage.LISTENING
        speechManager.clearErrorAndTranscript()
        speechManager.startListening(voiceLanguage)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            speechManager.setPermissionDeniedError()
            activeError = VoiceSpeechError(
                type = VoiceSpeechErrorType.PERMISSION_DENIED,
                message = "Microphone permission is needed for voice entry. You can allow permission or enter details manually."
            )
            stage = VoiceSheetStage.ERROR
        }
    }

    fun requestAndStart() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startVoiceRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        requestAndStart()
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.destroy()
        }
    }

    // Handle final transcript
    LaunchedEffect(speechState.finalTranscript) {
        val transcript = speechState.finalTranscript ?: return@LaunchedEffect
        if (transcript.isNotBlank() && stage == VoiceSheetStage.LISTENING) {
            rawTranscript = transcript
            stage = VoiceSheetStage.PARSING
            val parsed = onParseTranscript(transcript)
            applyParsedResult(transcript, parsed)
        }
    }

    // Handle SpeechRecognizer errors (auto-fallback to System Speech Prompt once if UNAVAILABLE)
    LaunchedEffect(speechState.error) {
        val err = speechState.error ?: return@LaunchedEffect
        if (stage == VoiceSheetStage.LISTENING) {
            if (err.type == VoiceSpeechErrorType.UNAVAILABLE && !triedSystemFallback) {
                launchSystemSpeechPrompt()
            } else {
                activeError = err
                stage = VoiceSheetStage.ERROR
            }
        }
    }

    val sheetBg = if (isDark) Plum950.copy(alpha = 0.96f) else Cream100.copy(alpha = 0.98f)
    val primaryText = if (isDark) Cream50 else Plum900
    val secondaryText = if (isDark) Taupe300 else Taupe700

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.cancelListening()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBg,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = if (isDark) Gold500.copy(alpha = 0.45f) else Plum700.copy(alpha = 0.35f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when (stage) {
                            VoiceSheetStage.LISTENING -> "Voice Entry"
                            VoiceSheetStage.PARSING -> "Analyzing Speech"
                            VoiceSheetStage.CONFIRMATION -> "Confirm Transaction"
                            VoiceSheetStage.ERROR -> "Voice Input Issue"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                    Surface(
                        color = Gold500.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.border(
                            1.dp,
                            Gold500.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                    ) {
                        Text(
                            text = if (voiceLanguage == "ta-IN") "TA-IN" else "EN-IN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Gold300 else GoldTextOnLight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        speechManager.cancelListening()
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close voice entry",
                        tint = secondaryText
                    )
                }
            }

            when (stage) {
                VoiceSheetStage.LISTENING -> {
                    ListeningContent(
                        partialText = speechState.partialText,
                        rmsDb = speechState.rmsDb,
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onCancel = {
                            speechManager.cancelListening()
                            onDismiss()
                        },
                        onDone = {
                            speechManager.finishListening()
                        },
                        onUseSystemMic = {
                            launchSystemSpeechPrompt()
                        },
                        onEnterManually = {
                            speechManager.cancelListening()
                            onSwitchToManual()
                        }
                    )
                }

                VoiceSheetStage.PARSING -> {
                    GlassCard(isDark = isDark, isElevated = true, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Gold500)
                            Text(
                                text = "Parsing \"$rawTranscript\"...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                VoiceSheetStage.ERROR -> {
                    val err = activeError ?: VoiceSpeechError(
                        VoiceSpeechErrorType.UNKNOWN,
                        "Could not capture voice input."
                    )
                    GlassCard(isDark = isDark, isElevated = true, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ExpenseDark.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (err.type == VoiceSpeechErrorType.PERMISSION_DENIED)
                                        Icons.Default.MicOff else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ExpenseDark,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = err.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryText,
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlassSecondaryButton(
                                    text = "Enter Manually",
                                    onClick = {
                                        speechManager.cancelListening()
                                        onSwitchToManual()
                                    },
                                    isDark = isDark,
                                    modifier = Modifier.weight(1f)
                                )
                                GlassGradientButton(
                                    text = "Try Again",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        requestAndStart()
                                    },
                                    isDark = isDark,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (err.type == VoiceSpeechErrorType.PERMISSION_DENIED) {
                                    TextButton(onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }) {
                                        Text("Open App Settings", color = Gold500, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    TextButton(onClick = { launchSystemSpeechPrompt() }) {
                                        Text("Use System Voice Dialog", color = Gold500, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                VoiceSheetStage.CONFIRMATION -> {
                    val parsedAmount = amountText.toDoubleOrNull()
                    val filteredCategories = remember(categories, selectedType) {
                        if (selectedType == null) categories
                        else categories.filter { it.type == selectedType }
                    }
                    val selectedCategory = remember(selectedCategoryId, filteredCategories, categories) {
                        categories.firstOrNull { it.id == selectedCategoryId }
                            ?: filteredCategories.firstOrNull()
                    }
                    val missingType = selectedType == null
                    val missingAmount = parsedAmount == null || parsedAmount <= 0.0

                    // Spoken transcript preview
                    if (rawTranscript.isNotBlank()) {
                        Text(
                            text = "Heard: \"$rawTranscript\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryText
                        )
                    }

                    if (missingType || missingAmount) {
                        Surface(
                            color = WarningDark.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, WarningDark.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = when {
                                    missingType && missingAmount ->
                                        "Please choose Expense or Income and enter the amount below."
                                    missingType ->
                                        "Could not tell if this is an Expense or Income. Please tap one below."
                                    else ->
                                        "Amount was not detected. Please enter the amount below."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Cream50 else Plum900,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    GlassCard(
                        isDark = isDark,
                        isElevated = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 1. Type selector (tappable)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Type",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (missingType) WarningDark else secondaryText
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    listOf("expense" to "Expense", "income" to "Income").forEach { (value, label) ->
                                        val isSelected = selectedType == value
                                        val accentColor = if (value == "income") IncomeDark else ExpenseDark
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) accentColor.copy(alpha = 0.22f)
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) accentColor
                                                    else if (missingType) WarningDark.copy(alpha = 0.7f)
                                                    else Gold500.copy(alpha = 0.28f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    selectedType = value
                                                    val matchingForType = categories.filter { it.type == value }
                                                    if (matchingForType.none { it.id == selectedCategoryId }) {
                                                        selectedCategoryId = matchingForType.firstOrNull()?.id
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) accentColor else primaryText
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Gold500.copy(alpha = 0.16f))

                            // 2. Amount (tappable to edit, displayed with INR grouping)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isEditingAmount = !isEditingAmount },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Amount (INR)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (missingAmount) WarningDark else secondaryText
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (parsedAmount != null && parsedAmount > 0.0)
                                                formatIndianInr(parsedAmount) else "Tap to enter",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (missingAmount) WarningDark else Gold500
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit amount",
                                            tint = Gold500,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (isEditingAmount) {
                                    OutlinedTextField(
                                        value = amountText,
                                        onValueChange = { next ->
                                            if (next.isEmpty() || next.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                amountText = next
                                            }
                                        },
                                        label = { Text("Enter amount in INR") },
                                        prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Gold500,
                                            unfocusedBorderColor = Gold500.copy(alpha = 0.4f),
                                            focusedTextColor = primaryText,
                                            unfocusedTextColor = primaryText
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(color = Gold500.copy(alpha = 0.16f))

                            // 3. Category (tappable to expand & pick from existing list)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { isEditingCategory = !isEditingCategory },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Category",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = selectedCategory?.name ?: "Select category",
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryText
                                        )
                                        Icon(
                                            if (isEditingCategory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Change category",
                                            tint = Gold500
                                        )
                                    }
                                }

                                if (isEditingCategory) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(filteredCategories, key = { it.id }) { cat ->
                                            val isSelected = selectedCategory?.id == cat.id
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) Gold500 else Plum800.copy(alpha = if (isDark) 0.7f else 0.12f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Gold500 else Gold500.copy(alpha = 0.25f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        selectedCategoryId = cat.id
                                                        if (selectedType == null) {
                                                            selectedType = cat.type
                                                        }
                                                        isEditingCategory = false
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = cat.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Plum900 else primaryText
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Gold500.copy(alpha = 0.16f))

                            // 4. Date (Today / Yesterday / Calendar Picker)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val displayDate = remember(selectedDate) {
                                    runCatching {
                                        dateFormatter.parse(selectedDate)?.let { displayDateFormatter.format(it) }
                                    }.getOrNull() ?: selectedDate
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Date",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Text(
                                        text = when (selectedDate) {
                                            todayStr -> "Today ($displayDate)"
                                            yesterdayStr -> "Yesterday ($displayDate)"
                                            else -> displayDate
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(todayStr to "Today", yesterdayStr to "Yesterday").forEach { (dVal, label) ->
                                        val isSel = selectedDate == dVal
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) Gold500 else Color.Transparent)
                                                .border(1.dp, Gold500.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                                .clickable { selectedDate = dVal },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Plum900 else primaryText
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, Gold500.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                            .clickable {
                                                val cal = Calendar.getInstance()
                                                runCatching {
                                                    dateFormatter.parse(selectedDate)?.let { cal.time = it }
                                                }
                                                DatePickerDialog(
                                                    context,
                                                    { _, y, m, day ->
                                                        val c = Calendar.getInstance()
                                                        c.set(y, m, day)
                                                        selectedDate = dateFormatter.format(c.time)
                                                    },
                                                    cal.get(Calendar.YEAR),
                                                    cal.get(Calendar.MONTH),
                                                    cal.get(Calendar.DAY_OF_MONTH)
                                                ).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Pick Date",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Gold500
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Gold500.copy(alpha = 0.16f))

                            // 5. Payment Method
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Payment Method",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = secondaryText
                                )
                                val methods = listOf(
                                    "upi" to "UPI",
                                    "cash" to "Cash",
                                    "card" to "Card",
                                    "net_banking" to "Net Banking",
                                    "bank_transfer" to "Bank Transfer"
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(methods, key = { it.first }) { (key, label) ->
                                        val isSel = selectedPaymentMethod == key
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) Gold500 else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (isSel) Gold500 else Gold500.copy(alpha = 0.3f),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { selectedPaymentMethod = key }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Plum900 else primaryText
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Gold500.copy(alpha = 0.16f))

                            // 6. Note (tappable to edit)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 40.dp)
                                        .clickable { isEditingNote = !isEditingNote },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Note",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = secondaryText
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = noteText.ifBlank { "Add note" },
                                            fontSize = 13.sp,
                                            color = primaryText
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit note",
                                            tint = Gold500,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (isEditingNote) {
                                    OutlinedTextField(
                                        value = noteText,
                                        onValueChange = { noteText = it },
                                        label = { Text("Transaction note") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Gold500,
                                            unfocusedBorderColor = Gold500.copy(alpha = 0.4f),
                                            focusedTextColor = primaryText,
                                            unfocusedTextColor = primaryText
                                        )
                                    )
                                }
                            }

                            if (usedSmartParser) {
                                Text(
                                    text = "Smart parsed via Gemini",
                                    fontSize = 11.sp,
                                    color = secondaryText
                                )
                            }
                        }
                    }

                    // Action Buttons: Try again & Save (never auto-saves)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSecondaryButton(
                            text = "Try again",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                requestAndStart()
                            },
                            isDark = isDark,
                            modifier = Modifier.weight(1f)
                        )
                        GlassGradientButton(
                            text = "Save",
                            onClick = {
                                val amt = amountText.toDoubleOrNull()
                                val txType = selectedType
                                if (amt != null && amt > 0.0 && txType != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveTransaction(
                                        amt,
                                        selectedCategory?.id,
                                        selectedPaymentMethod,
                                        noteText.takeIf { it.isNotBlank() },
                                        selectedDate,
                                        txType
                                    )
                                }
                            },
                            isDark = isDark,
                            enabled = !missingType && !missingAmount,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    TextButton(
                        onClick = onSwitchToManual,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Type details manually instead",
                            color = secondaryText,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ListeningContent(
    partialText: String,
    rmsDb: Float,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onUseSystemMic: () -> Unit,
    onEnterManually: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val dynamicBoost = (rmsDb / 18f).coerceIn(0f, 0.16f)

    GlassCard(
        isDark = isDark,
        isElevated = true,
        isHeroCard = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier.size(116.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer pulse ring
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pulseScale + dynamicBoost)
                        .clip(CircleShape)
                        .background(Gold500.copy(alpha = 0.18f))
                )
                // Inner gold mic circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Gold300, Gold500, Gold700)
                            )
                        )
                        .semantics { contentDescription = "Listening for transaction" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Plum900,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Text(
                text = "Listening… Speak naturally",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryText
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Plum900.copy(alpha = 0.65f) else Cream200.copy(alpha = 0.8f))
                    .border(1.dp, Gold500.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (partialText.isNotBlank()) "\"$partialText\""
                    else "Try: \"spent 486 on swiggy dinner yesterday by UPI\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (partialText.isNotBlank()) primaryText else secondaryText,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassSecondaryButton(
                    text = "Cancel",
                    onClick = onCancel,
                    isDark = isDark,
                    modifier = Modifier.weight(1f)
                )
                if (partialText.isNotBlank()) {
                    GlassGradientButton(
                        text = "Done",
                        onClick = onDone,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    GlassSecondaryButton(
                        text = "Type Manually",
                        onClick = onEnterManually,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onUseSystemMic) {
                    Text(
                        text = "Use System Mic",
                        fontSize = 12.sp,
                        color = Gold500,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onEnterManually) {
                    Text(
                        text = "Enter manually",
                        fontSize = 12.sp,
                        color = secondaryText
                    )
                }
            }
        }
    }
}
