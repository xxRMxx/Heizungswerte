package com.example.heizungswerte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.heizungswerte.data.AppDatabase
import com.example.heizungswerte.data.Radiator
import com.example.heizungswerte.data.ReadingRepository
import com.example.heizungswerte.ui.HeizungswerteTheme
import com.example.heizungswerte.viewmodel.MainViewModel
import com.example.heizungswerte.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "heizung-db"
        ).build()

        val repository = ReadingRepository(db.readingDao())
        
        // Manual Import logic
        importInitialData(db, this)

        val factory = ViewModelFactory(repository, applicationContext)

        setContent {
            HeizungswerteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HeizungApp(factory)
                }
            }
        }
    }
}

private fun importInitialData(db: AppDatabase, context: android.content.Context) {
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val count = db.readingDao().getCount()
            if (count == 0) {
                val jsonString = context.assets.open("readings_import.json").bufferedReader().use { it.readText() }
                val readings = Json.decodeFromString<List<com.example.heizungswerte.data.Reading>>(jsonString)
                db.readingDao().insertReadings(readings)
                prefs.edit().putBoolean("data_imported", true).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeizungApp(factory: ViewModelFactory) {
    val viewModel: MainViewModel = viewModel(factory = factory)
    var showHistory by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle Back Navigation
    BackHandler(enabled = showHistory || editingDate != null) {
        if (editingDate != null) {
            editingDate = null
        } else if (showHistory) {
            showHistory = false
        }
    }

    if (showDeleteDialog && editingDate != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchten Sie den Eintrag für diesen Tag wirklich unwiderruflich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReadings(editingDate!!) {
                            editingDate = null
                            showHistory = true
                            scope.launch {
                                snackbarHostState.showSnackbar("Eintrag gelöscht")
                            }
                        }
                        showDeleteDialog = false
                    }
                ) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (editingDate != null) "Eintrag bearbeiten" else if (showHistory) "Historie" else "Neue Ablesung") },
                navigationIcon = {
                    if (editingDate != null) {
                        IconButton(onClick = { editingDate = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                        }
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (editingDate != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    } else {
                        IconButton(onClick = { 
                            if (showHistory) viewModel.clearInputs()
                            showHistory = !showHistory 
                        }) {
                            Icon(
                                imageVector = if (showHistory) Icons.Default.DateRange else Icons.Default.History,
                                contentDescription = if (showHistory) "Eingabe" else "Historie"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (editingDate != null) {
                EntryScreen(viewModel, editingDate!!) {
                    editingDate = null
                    showHistory = true
                    scope.launch {
                        snackbarHostState.showSnackbar("Eintrag aktualisiert")
                    }
                }
            } else if (showHistory) {
                HistoryScreen(viewModel) { date ->
                    editingDate = date
                }
            } else {
                EntryScreen(viewModel, isNewEntry = true) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Ablesung erfolgreich gespeichert")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    viewModel: MainViewModel, 
    initialDate: Long = System.currentTimeMillis(),
    isNewEntry: Boolean = false,
    onSaved: () -> Unit = {}
) {
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    
    // Detect keyboard visibility to clear focus when keyboard is closed
    val isKeyboardVisible = WindowInsets.isImeVisible
    
    // Track if anything is focused to intercept back press
    var isAnyFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible && isAnyFieldFocused) {
            focusManager.clearFocus()
            isAnyFieldFocused = false
        }
    }

    BackHandler(enabled = isAnyFieldFocused) {
        focusManager.clearFocus()
        isAnyFieldFocused = false
    }

    // Load data when the screen is first shown or when selectedDate changes
    LaunchedEffect(selectedDate) {
        if (isNewEntry && selectedDate == initialDate) {
            viewModel.clearInputs()
        } else {
            viewModel.loadReadingsForDate(selectedDate)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = datePickerState.selectedDateMillis ?: selectedDate
                    viewModel.loadReadingsForDate(selectedDate)
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStr = sdf.format(Date(selectedDate))
            Text(
                text = "Datum: $dateStr",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "Datum wählen")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            Radiator.entries.forEach { radiator ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("${radiator.roomName} (${radiator.id})", modifier = Modifier.weight(1f))
                    
                    val textValue = viewModel.radiatorValues[radiator.id] ?: ""
                    var textFieldValueState by remember {
                        mutableStateOf(TextFieldValue(text = textValue))
                    }
                    
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    
                    // Sync internal state with viewModel state when it changes
                    LaunchedEffect(textValue) {
                        if (textFieldValueState.text != textValue) {
                            textFieldValueState = textFieldValueState.copy(text = textValue)
                        }
                    }
                    
                    // Select all when focused
                    LaunchedEffect(isFocused) {
                        if (isFocused) {
                            isAnyFieldFocused = true
                            if (textFieldValueState.text.isNotEmpty()) {
                                delay(100) // Delay to ensure selection happens after cursor placement
                                textFieldValueState = textFieldValueState.copy(
                                    selection = TextRange(0, textFieldValueState.text.length)
                                )
                            }
                        } else {
                            // We don't set to false here globally because other fields might be focused
                        }
                    }

                    OutlinedTextField(
                        value = textFieldValueState,
                        onValueChange = { 
                            textFieldValueState = it
                            viewModel.radiatorValues[radiator.id] = it.text 
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp),
                        interactionSource = interactionSource,
                        singleLine = true
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    "Gesamt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                val total = viewModel.radiatorValues.values.sumOf { it.toIntOrNull() ?: 0 }
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.width(100.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            val noteInteractionSource = remember { MutableInteractionSource() }
            val isNoteFocused by noteInteractionSource.collectIsFocusedAsState()
            LaunchedEffect(isNoteFocused) {
                if (isNoteFocused) isAnyFieldFocused = true
            }

            OutlinedTextField(
                value = viewModel.dayNote.value,
                onValueChange = { viewModel.dayNote.value = it },
                label = { Text("Beschreibung / Hinweis") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                interactionSource = noteInteractionSource
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.saveReadings(selectedDate, onSaved) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Speichern")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onDateSelected: (Long) -> Unit) {
    val dates by viewModel.uniqueDates.collectAsState()
    val readings by viewModel.allReadings.collectAsState()
    val notes by viewModel.allNotes.collectAsState()
    val stats = viewModel.getStats(readings, dates)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Verbrauchsübersicht",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Seit Jahresbeginn:", style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.totalYearToDate}", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Seit letzter Ablesung:", style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.lastDifference}", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }

        items(dates) { date ->
            val totalForDate = readings.filter { it.dateMillis == date }.sumOf { it.value }
            val noteForDate = notes.find { it.dateMillis == date }?.note
            Card(
                onClick = { onDateSelected(date) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        val dateStr = sdf.format(Date(date))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Gesamt: $totalForDate",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (!noteForDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = noteForDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}
