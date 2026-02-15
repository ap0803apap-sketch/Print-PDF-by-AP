package com.ap.print.pdf

import android.content.ComponentName
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.ServiceConnection

import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.datastore.preferences.preferencesDataStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    // Helper to extract URI from intent
    private fun getSharedUri(intent: Intent?): Uri? {
        return if (intent?.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Get URI from initial launch or share
        val initialUri = getSharedUri(intent)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode = themeViewModel.themeMode.collectAsState().value
            val dynamicColor = themeViewModel.dynamicColor.collectAsState().value

            // Create MainViewModel here to access it if needed
            val mainViewModel: MainViewModel = viewModel()

            // 2. Handle new Intents (Share when app is already open)
            DisposableEffect(Unit) {
                val listener = androidx.core.util.Consumer<Intent> { newIntent ->
                    val newUri = getSharedUri(newIntent)
                    if (newUri != null) {
                        mainViewModel.loadPdf(newUri, this@MainActivity)
                    }
                }
                addOnNewIntentListener(listener)
                onDispose { removeOnNewIntentListener(listener) }
            }

            PrintPdfTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                MainScreen(
                    initialUri = initialUri,
                    currentThemeMode = themeMode,
                    onThemeModeChange = { themeViewModel.setTheme(it) },
                    isDynamicColor = dynamicColor,
                    onDynamicColorChange = { themeViewModel.setDynamic(it) },
                    viewModel = mainViewModel // Pass the same instance
                )
            }
        }
    }

    // Handle PDF share when app already open (Required for singleTask/singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

// --- VIEW MODEL ---
class MainViewModel : ViewModel() {
    var pdfService: PdfProcessingService? = null
    var isBound by mutableStateOf(false)
    var settings by mutableStateOf(PdfSettings())
    var processingState by mutableStateOf(ProcessingState())
    var currentFileName by mutableStateOf("document")
    var currentUri by mutableStateOf<Uri?>(null)

    private val defaultSettings = PdfSettings(
        pageSelectionType = PageSelectionType.ALL,
        customPageString = "",
        pagesPerSheet = 1,
        scaleType = ScaleType.FIT,
        customScalePercent = 100,
        pageSizeName = "A4",
        orientation = Orientation.PORTRAIT,
        showBorder = false,
        printingMode = PrintingMode.SINGLE_SIDED,
        marginType = MarginType.PRESET,
        marginPreset = "Fit to Paper",
        customMarginMm = 0f
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PdfProcessingService.LocalBinder
            pdfService = binder.getService()
            isBound = true
            setupServiceCallbacks()
        }
        override fun onServiceDisconnected(name: ComponentName?) { isBound = false }
    }

    private val Context.dataStore by preferencesDataStore("theme_prefs")

    fun bindService(context: Context) {
        val intent = Intent(context, PdfProcessingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupServiceCallbacks() {
        pdfService?.onProgress = { prog, msg ->
            processingState = processingState.copy(progress = prog, message = msg, isProcessing = prog < 1.0f)
        }
        pdfService?.onPreviewReady = { bitmap ->
            processingState = processingState.copy(previewBitmap = bitmap, isProcessing = false)
        }
    }

    fun loadPdf(uri: Uri, context: Context) {
        currentUri = uri
        processingState = processingState.copy(isProcessing = true, message = "Importing...")

        // Safety check for content resolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if(index != -1) currentFileName = cursor.getString(index).substringBeforeLast(".")
                }
            }
        } catch (e: Exception) {
            currentFileName = "Shared_Document"
        }

        pdfService?.loadPdf(uri, context.contentResolver)
    }

    fun refreshPreview(context: Context) {
        currentUri?.let { uri ->
            processingState = processingState.copy(isProcessing = true, message = "Refreshing preview...")
            pdfService?.loadPdf(uri, context.contentResolver)
        }
    }

    fun resetSettings() {
        settings = defaultSettings
    }

    fun savePdf(context: Context) {
        processingState = processingState.copy(isProcessing = true)
        pdfService?.savePdf(context, settings, currentFileName)
    }
}

// --- UI SCREENS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialUri: Uri?,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    isDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    // Bind PDF service
    LaunchedEffect(Unit) {
        viewModel.bindService(context)
    }

    // Load shared PDF (Initial launch)
    LaunchedEffect(initialUri, viewModel.isBound) {
        if (initialUri != null && viewModel.isBound && viewModel.currentUri == null) {
            viewModel.loadPdf(initialUri, context)
        }
    }

    // File picker
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.loadPdf(it, context) }
        }

    // Settings Screen
    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange,
            isDynamicColor = isDynamicColor,
            onDynamicColorChange = onDynamicColorChange
        )
    } else {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Print PDF", fontWeight = FontWeight.ExtraBold) },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },

            floatingActionButton = {
                if (!viewModel.processingState.isProcessing &&
                    viewModel.processingState.previewBitmap != null
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.savePdf(context) },
                        containerColor = DeepPlum,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (viewModel.processingState.previewBitmap == null) {

                    Button(
                        onClick = { filePicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .padding(top = 80.dp)
                            .height(72.dp)
                            .width(240.dp),
                        shape = RoundedCornerShape(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExpressivePink,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick PDF File", fontWeight = FontWeight.Bold)
                    }

                } else {

                    Spacer(Modifier.height(12.dp))

                    val bitmap = viewModel.processingState.previewBitmap
                    val isPortrait =
                        bitmap?.width?.toFloat()?.let { w -> bitmap.height?.let { h -> w < h } }
                            ?: true

                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .then(
                                if (isPortrait) {
                                    Modifier.height(320.dp).aspectRatio(0.7f)
                                } else {
                                    Modifier.height(240.dp).aspectRatio(1.4f)
                                }
                            ),
                        shape = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(6.dp)
                    ) {
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Button(
                            onClick = { filePicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ExpressivePink,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.refreshPreview(context) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepPlum,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.resetSettings() },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    ControlPanel(viewModel)
                }

                if (viewModel.processingState.isProcessing) {
                    LinearProgressIndicator(
                        progress = { viewModel.processingState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(8.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}


@Composable
fun ControlPanel(viewModel: MainViewModel) {
    val settings = viewModel.settings
    val availableSizes = PageSizeUtils.getAvailableSizes()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        ExpressiveSection(title = "Page Selection") {
            DropdownSelector(
                label = "Pages",
                options = PageSelectionType.values().map { it.name },
                selected = settings.pageSelectionType.name,
                onSelect = { newValue ->
                    viewModel.settings = settings.copy(pageSelectionType = PageSelectionType.valueOf(newValue))
                }
            )
            if (settings.pageSelectionType == PageSelectionType.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.customPageString,
                    onValueChange = { newValue ->
                        viewModel.settings = settings.copy(customPageString = newValue)
                    },
                    label = { Text("e.g. 1, 2, 4-6") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }

        ExpressiveSection(title = "Print Mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.printingMode == PrintingMode.SINGLE_SIDED,
                        onClick = { viewModel.settings = settings.copy(printingMode = PrintingMode.SINGLE_SIDED) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("1-Sided (Single-sided)", fontWeight = FontWeight.Medium)
                        Text("Print pages as they are", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.printingMode == PrintingMode.DOUBLE_SIDED,
                        onClick = { viewModel.settings = settings.copy(printingMode = PrintingMode.DOUBLE_SIDED) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("2-Sided (Double-sided)", fontWeight = FontWeight.Medium)
                        Text("Continuous pages with blank pages between", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ExpressiveSection(title = "Margins") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.marginType == MarginType.PRESET,
                        onClick = { viewModel.settings = settings.copy(marginType = MarginType.PRESET) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Preset Margins", fontWeight = FontWeight.Medium)
                }

                if (settings.marginType == MarginType.PRESET) {
                    Spacer(Modifier.height(4.dp))
                    DropdownSelector(
                        label = "Margin Preset",
                        options = listOf("Fit to Paper", "Normal", "Narrow", "Wide", "Fit to Printable Area"),
                        selected = settings.marginPreset,
                        onSelect = { newValue ->
                            viewModel.settings = settings.copy(marginPreset = newValue)
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.marginType == MarginType.CUSTOM,
                        onClick = { viewModel.settings = settings.copy(marginType = MarginType.CUSTOM) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Custom Margin", fontWeight = FontWeight.Medium)
                }

                if (settings.marginType == MarginType.CUSTOM) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = settings.customMarginMm.toString(),
                        onValueChange = { newValue ->
                            val value = newValue.toFloatOrNull() ?: 0f
                            viewModel.settings = settings.copy(customMarginMm = value)
                        },
                        label = { Text("Margin (mm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        suffix = { Text("mm") }
                    )
                }
            }
        }

        ExpressiveSection(title = "Layout & Border") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    DropdownSelector(
                        label = "Pages per sheet",
                        options = listOf("1", "2", "4", "6", "9", "16"),
                        selected = settings.pagesPerSheet.toString(),
                        onSelect = { newValue ->
                            viewModel.settings = settings.copy(pagesPerSheet = newValue.toInt())
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DropdownSelector(
                        label = "Paper Size",
                        options = availableSizes,
                        selected = settings.pageSizeName,
                        onSelect = { newValue ->
                            viewModel.settings = settings.copy(pageSizeName = newValue)
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Page Border Switch
            Row(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.settings = settings.copy(showBorder = !settings.showBorder) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Page Borders", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = settings.showBorder, onCheckedChange = { newValue ->
                    viewModel.settings = settings.copy(showBorder = newValue)
                })
            }
        }

        ExpressiveSection(title = "Page Scale") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.scaleType == ScaleType.FIT,
                        onClick = { viewModel.settings = settings.copy(scaleType = ScaleType.FIT) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Fit to Page", fontWeight = FontWeight.Medium)
                        Text("Auto-scale to fit page size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.scaleType == ScaleType.CUSTOM,
                        onClick = { viewModel.settings = settings.copy(scaleType = ScaleType.CUSTOM) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Custom Scale", fontWeight = FontWeight.Medium)
                        Text("Set custom scale percentage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (settings.scaleType == ScaleType.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = settings.customScalePercent.toString(),
                            onValueChange = { newValue ->
                                val value = newValue.toIntOrNull() ?: 100
                                val clampedValue = value.coerceIn(10, 200)
                                viewModel.settings = settings.copy(customScalePercent = clampedValue)
                            },
                            label = { Text("Scale %") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            suffix = { Text("%") }
                        )
                        Text(
                            "${settings.customScalePercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = ExpressivePink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Scale slider
                    Slider(
                        value = settings.customScalePercent.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.settings = settings.copy(customScalePercent = newValue.toInt())
                        },
                        valueRange = 10f..200f,
                        steps = 18,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("10%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("200%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        ExpressiveSection(title = "Orientation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Orientation.PORTRAIT, Orientation.LANDSCAPE).forEach { mode ->
                    val isSelected = settings.orientation == mode
                    Button(
                        onClick = { viewModel.settings = settings.copy(orientation = mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ExpressivePink else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text(mode.name) }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = DeepPlum, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLavender.copy(alpha = 0.5f)),
            content = { Column(Modifier.padding(16.dp)) { content() } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true, value = selected, onValueChange = {}, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    isDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = currentThemeMode == AppThemeMode.SYSTEM, onClick = { onThemeModeChange(AppThemeMode.SYSTEM) })
                Text("System")
                Spacer(Modifier.width(8.dp))
                RadioButton(selected = currentThemeMode == AppThemeMode.LIGHT, onClick = { onThemeModeChange(AppThemeMode.LIGHT) })
                Text("Light")
                Spacer(Modifier.width(8.dp))
                RadioButton(selected = currentThemeMode == AppThemeMode.DARK, onClick = { onThemeModeChange(AppThemeMode.DARK) })
                Text("Dark")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDynamicColor, onCheckedChange = onDynamicColorChange)
                    Text("Use Dynamic Color")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Permissions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            }) {
                Text("Open App Settings")
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("About Developer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Developed by: AP",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Email - Clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val emailIntent = Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:ap0803apap@gmail.com")
                                ).apply {
                                    putExtra(Intent.EXTRA_SUBJECT, "Print PDF App Feedback")
                                }

                                try {
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    // Fallback to chooser if direct launch fails
                                    val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "message/rfc822"
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("ap0803apap@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, "Print PDF App Feedback")
                                    }
                                    context.startActivity(
                                        Intent.createChooser(fallbackIntent, "Send Email")
                                    )
                                }
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "ap0803apap@gmail.com",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }



                    // GitHub - Clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                uriHandler.openUri("https://github.com/your-username/print-pdf")
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "View Source Code on GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "© 2026 Print PDF by AP. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}