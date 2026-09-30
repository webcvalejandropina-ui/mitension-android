package com.example.mitension

import android.Manifest
import android.app.Application
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.example.mitension.data.*
import com.example.mitension.model.*
import com.example.mitension.reminders.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One application-wide store prevents activity/receiver copies overwriting each other's state. */
class TensionApplication : Application() { val readings by lazy { ReadingStore(this) } }
val Context.store get() = (applicationContext as TensionApplication).readings
private val LocalClockVersion = compositionLocalOf { 0 }

/** Native Android entry point, deliberately independent of SwiftUI, web, auth and payment services. */
class MainActivity : ComponentActivity() {
    private var clockVersion by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { MiTensionTheme { CompositionLocalProvider(LocalClockVersion provides clockVersion) { App() } } }
    }
    override fun onResume() {
        super.onResume(); clockVersion++ // Refresh local dates/periods after returning, without losing drafts.
        runCatching { Reminders.reconcile(this) }
    }
}

@Composable internal fun tx(key: String) = LocalContext.current.t(key)
private fun Reading.time() = localTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
private fun LocalDate.title() = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

/** Explicit light/dark palettes keep reference indicators readable in both themes. */
@Composable fun MiTensionTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Match DashboardView.swift's adaptive iPhone palette, without Android dynamic recolouring.
    val colors = if (dark) darkColorScheme(primary = Color(0xFF4FDEC8), background = Color(0xFF0C1218),
        surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7), onBackground = Color(0xFFF2F2F7),
        onSurfaceVariant = Color(0xFFB6B6BF), secondary = Color(0xFFABA3FF),
        surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHighest = Color(0xFF2C2C2E))
    else lightColorScheme(primary = Color(0xFF0B796E), background = Color(0xFFF2F7F8),
        surface = Color.White, onSurface = Color(0xFF071826), onBackground = Color(0xFF071826),
        onSurfaceVariant = Color(0xFF62666C), secondary = Color(0xFF5856D6),
        surfaceContainer = Color.White, surfaceContainerHighest = Color(0xFFE9EFF0))
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
    val context = LocalContext.current
    val readings by context.store.readings.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var homeTitle by remember { mutableStateOf("Mis mediciones") }
    var error by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }; val scope = rememberCoroutineScope()
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val rows = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { Excel.read(it) } }
                val count = withContext(Dispatchers.IO) { context.store.importRows(rows) }
                Reminders.reconcile(context)
                snack.showSnackbar(context.t("Tomas restauradas: %ld").replace("%ld", count.toString()))
            }.onFailure { error = context.t("No se pudo importar el Excel. Usa un archivo exportado por Mi Tensión con registros válidos.") }
        }
    }
    fun share(excel: Boolean, selected: List<Reading> = readings) { scope.launch {
        runCatching {
            val file = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                if (excel) File(directory, "Mi-Tension-${java.util.UUID.randomUUID()}.xlsx").apply {
                    writeBytes(Excel.write(selected, listOf(context.t("Fecha y hora"), context.t("Momento"), context.t("Sistólica"),
                        context.t("Diastólica"), context.t("Pulso"), context.t("Notas"), "ID", context.t("Medicamentos de esta toma")),
                        periods = context.t("Mañana") to context.t("Noche")))
                } else PdfReport.make(context, selected)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).setType(if (excel) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("Mi Tension", uri) }
            context.startActivity(Intent.createChooser(intent, context.t("Compartir o imprimir")))
        }.onFailure { error = context.t("No se pudo exportar el archivo.") }
    } }
    val title = when(route) { "add" -> "Guardar nueva toma"; "more" -> "Cuida tu rutina"; "guide" -> "Guía y privacidad"
        "reminders" -> "Alertas"; "import" -> "Importar registros de Excel"; "wear" -> "Reloj · Beta"; else -> homeTitle }
    Scaffold(topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (route == "home") Image(painterResource(R.drawable.pineapple_mark), null, Modifier.size(36.dp))
        Text(if(route == "wear") stringResource(R.string.wear_beta_badge) else tx(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    } }, navigationIcon = { if(route != "home") IconButton(onClick = { route = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tx("Cerrar")) } },
        actions = { if(route == "home") { IconButton(onClick = { route = "reminders" }) { Icon(Icons.Default.Notifications, tx("Alertas")) }
            IconButton(onClick = { route = "more" }) { Icon(Icons.Default.MoreVert, tx("Más")) } } }) },
        snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(route) {
                "home" -> Home(readings, { route = "add" }, { share(false, it) }, { selected ->
                    runCatching { ReportPrinter.print(context, selected) }.onFailure { error = context.t("No se pudo exportar el archivo.") }
                }, { reading ->
                    runCatching { context.store.delete(reading.id); Reminders.reconcile(context) }.onFailure { error = context.t("No se pudo guardar la toma en este iPhone. Inténtalo de nuevo.") }
                }, { homeTitle = it })
                "add" -> AddReading { rows ->
                    try { context.store.add(rows); Reminders.reconcile(context); route = "home"; null }
                    catch (e: Exception) { context.t("No se pudo guardar la toma en este iPhone. Inténtalo de nuevo.") }
                }
                "more" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tx("Guía, avisos y tus datos, en un solo lugar."), style = MaterialTheme.typography.bodyLarge)
                    Tool("Alertas", Icons.Default.Notifications) { route = "reminders" }
                    Tool("Guía y privacidad", Icons.Default.Info) { route = "guide" }
                    Tool("Exportar a Excel", Icons.Default.Share) { share(true) }
                    Tool("Importar registros de Excel", Icons.Default.Add) { route = "import" }
                    Tool(stringResource(R.string.wear_beta_badge), Icons.Default.Info) { route = "wear" }
                    Text(tx("La copia contiene datos de salud. Guárdala en un lugar privado y compártela solo con personas de confianza."))
                }
                "guide" -> Guide()
                "reminders" -> ReminderSettings()
                "import" -> ImportInstructions { launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }
                "wear" -> WearBetaScreen()
            }
        }
    }
    if(context.store.loadError) AlertDialog(onDismissRequest = {}, title = { Text(tx("Histórico")) },
        text = { Text(tx("No se pudo leer el histórico. Se conserva el archivo original; no guardes nuevas tomas hasta recuperarlo.")) },
        confirmButton = { TextButton(onClick = { (context as? android.app.Activity)?.finish() }) { Text(tx("Cerrar")) } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text(tx("Cerrar")) } }) }
}

@Composable private fun Tool(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit) {
    Card(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(tx(title), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Swipe navigation is native pager state; menu taps and swipes share the same selected page. */
@Composable private fun Home(readings: List<Reading>, add: () -> Unit, share: (List<Reading>) -> Unit, print: (List<Reading>) -> Unit, delete: (Reading) -> Unit, title: (String) -> Unit) {
    val clock = LocalClockVersion.current
    val days = remember(readings, clock) { grouped(readings) }
    val pager = rememberPagerState { 4 }; val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    var pending by remember { mutableStateOf<Reading?>(null) }
    var historyDays by rememberSaveable { mutableStateOf<Int?>(7) }
    var medicalDays by rememberSaveable { mutableStateOf<Int?>(30) }
    LaunchedEffect(pager.currentPage) { title(listOf("Mis mediciones", "Histórico", "Gráficas", "Vista médica")[pager.currentPage]) }
    Column {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val selectedDays = if(page == 3) medicalDays else historyDays
            val visible = readingsInLastDays(readings, if(page == 2) null else selectedDays)
            val visibleGroups = grouped(visible)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if(page == 0) {
                    item { LocalNotice() }
                    item { LatestReading(readings.firstOrNull()) }
                    item { WeeklySummary(readings) }
                    item { Text(tx("Registro de tomas"), style = MaterialTheme.typography.titleLarge) }
                }
                if(page == 2) item { Chart(readings) }
                else {
                    item { PeriodPicker(if(page == 3) listOf(30, 90, null) else listOf(7, 30, null), selectedDays) {
                        if(page == 3) medicalDays = it else historyDays = it
                    } }
                    if(page == 3) item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReportSummary(visible)
                        OutlinedButton({ share(visible) }, Modifier.fillMaxWidth(), enabled = visible.isNotEmpty(), shape = RoundedCornerShape(16.dp)) { Text(tx("Compartir o imprimir")) }
                        OutlinedButton({ print(visible) }, Modifier.fillMaxWidth(), enabled = visible.isNotEmpty(), shape = RoundedCornerShape(16.dp)) { Text(tx("Imprimir")) }
                    } }
                    if(visible.isEmpty()) item { Text(tx("Todavía no hay tomas")) }
                    visibleGroups.forEach { (day, periods) ->
                        item(key = day.toString()) {
                            if(page == 3) MedicalDay(day, periods) else {
                            Text(day.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            periods.forEach { (period, rows) ->
                                Card(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(tx(if(period == "morning") "Mañana" else "Noche"), color = if(period == "morning") morningAccent() else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                        rows.forEachIndexed { index, row ->
                                            if(index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                            ReadingCard(row, { pending = row })
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
        if(pager.currentPage == 0) Button(add, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 20.dp).heightIn(min = 54.dp),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF071826), contentColor = Color.White)) {
            Icon(Icons.Default.AddCircle, null); Spacer(Modifier.width(8.dp)); Text(tx("Guardar nueva toma"), fontWeight = FontWeight.SemiBold)
        }
        // The bottom menu and swipe pager are one state, as on iPhone.
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .pointerInput(pager, layoutDirection) {
                var distance = 0f
                detectHorizontalDragGestures(onDragStart = { distance = 0f }, onHorizontalDrag = { change, delta ->
                    distance += delta; change.consume()
                }, onDragEnd = {
                    if(kotlin.math.abs(distance) > 35.dp.toPx()) {
                        val direction = (if(distance < 0) 1 else -1) * (if(layoutDirection == LayoutDirection.Rtl) -1 else 1)
                        scope.launch { pager.animateScrollToPage((pager.currentPage + direction).coerceIn(0, 3)) }
                    }
                })
            }.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.Info, Icons.Default.List)
            listOf("Resumen", "Histórico", "Gráficas", "Médico").forEachIndexed { index, label ->
                val selected = pager.currentPage == index
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if(selected) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                    .selectable(selected, onClick = { scope.launch { pager.animateScrollToPage(index) } })
                    .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(icons[index], null, Modifier.size(19.dp), tint = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tx(label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    pending?.let { row -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(tx("Eliminar toma")) },
        text = { Text(tx("La toma se borrará del almacenamiento local del iPhone.")) },
        confirmButton = { TextButton(onClick = { delete(row); pending = null }) { Text(tx("Eliminar toma")) } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(tx("Cancelar")) } }) }
}

/** Compact day/period report mirrors iPhone's doctor view, rather than oversized editable cards. */
@Composable private fun MedicalDay(day: LocalDate, periods: Map<String, List<Reading>>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(day.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            listOf("morning", "evening").forEachIndexed { index, period ->
                if(index > 0) HorizontalDivider()
                Text(tx(if(index == 0) "Mañana" else "Noche"), fontWeight = FontWeight.SemiBold,
                    color = if(index == 0) morningAccent() else MaterialTheme.colorScheme.secondary)
                val rows = periods[period].orEmpty()
                if(rows.isEmpty()) Text(tx("Sin tomas en este periodo"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.time(), style = MaterialTheme.typography.bodySmall)
                        Text("${row.systolic} / ${row.diastolic} mmHg", fontWeight = FontWeight.Bold)
                    }
                    row.pulse?.let { Text("$it ${tx("lpm")}", style = MaterialTheme.typography.bodySmall) }
                    if(row.note.isNotBlank()) Text(row.note, style = MaterialTheme.typography.bodySmall)
                    row.medications.forEach { Text(it.description, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Segmented period selection mirrors the iPhone filters; export uses precisely this selection. */
@Composable private fun PeriodPicker(options: List<Int?>, selected: Int?, change: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(4.dp)) {
        options.forEach { days ->
            val label = when(days) { 7 -> "7 días"; 30 -> "30 días"; 90 -> "90 días"; else -> "Todo" }
            Text(tx(label), Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if(days == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                .selectable(days == selected, onClick = { change(days) }).padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun ReportSummary(readings: List<Reading>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Mediciones" to readings.size.toString(), "Promedio" to if(readings.isEmpty()) "— / —" else
            "${readings.sumOf { it.systolic } / readings.size} / ${readings.sumOf { it.diastolic } / readings.size}").forEach { (label, value) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tx(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Amber needs a deeper shade on white and a brighter shade on dark cards. */
@Composable private fun morningAccent() = if(isSystemInDarkTheme()) Color(0xFFFFB956) else Color(0xFFA65A00)

@Composable private fun LocalNotice() {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(!prefs.getBoolean("noticeDismissed", false)) }
    if(visible) Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .09f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tx("Guardado en este iPhone"), Modifier.weight(1f))
            IconButton(onClick = { prefs.edit().putBoolean("noticeDismissed", true).apply(); visible = false }) { Icon(Icons.Default.Close, tx("Cerrar")) }
        }
    }
}

/** Same fixed ink/aqua hero and typography hierarchy as the iPhone last-reading card. */
@Composable private fun LatestReading(reading: Reading?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF071826), Color(0xFF0C3441)))).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(tx("ÚLTIMA TOMA"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = Color.White.copy(alpha = .68f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(reading?.systolic?.toString() ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("/", fontSize = 28.sp, color = Color(0xFF37D6C0))
            Text(reading?.diastolic?.toString() ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("mmHg", fontSize = 12.sp, color = Color.White.copy(alpha = .72f))
        }
        if(reading == null) Text(tx("Pulsa “Guardar nueva toma” para empezar."), color = Color.White.copy(alpha = .75f))
        else {
            Text(tx(if(reading.localTime().hour < 14) "Mañana" else "Noche") + " · " + reading.time(), color = Color.White.copy(alpha = .75f))
            // Keep the explanation and medical semantics shared with historical cards.
            Text(tx("Sistólica (alta)") + ": " + tx(if(reading.systolic >= 135) "Sobre la referencia" else "Por debajo de la referencia"), color = if(reading.systolic >= 135) Color(0xFFABA3FF) else Color(0xFFD8E8EC), fontSize = 12.sp)
            Text(tx("Diastólica (baja)") + ": " + tx(if(reading.diastolic >= 85) "Sobre la referencia" else "Por debajo de la referencia"), color = if(reading.diastolic >= 85) Color(0xFFABA3FF) else Color(0xFFD8E8EC), fontSize = 12.sp)
            if(reading.systolic >= 180 || reading.diastolic >= 120) Text(tx("Medición muy elevada"), color = Color(0xFFFFB4AB))
        }
    }
}

@Composable private fun WeeklySummary(readings: List<Reading>) {
    val week = readingsInLastDays(readings, 7)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("MEDIA 7 DÍAS" to if(week.isEmpty()) "— / —" else "${week.sumOf { it.systolic } / week.size} / ${week.sumOf { it.diastolic } / week.size}",
            "ESTA SEMANA" to week.size.toString()).forEachIndexed { index, (label, value) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tx(label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if(index == 0) "mmHg" else tx("tomas"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun ReadingCard(row: Reading, delete: (() -> Unit)?) {
    val context = LocalContext.current
    var explanation by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.time(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.systolic} / ${row.diastolic}", fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("mmHg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        row.pulse?.let { Text("$it ${tx("lpm")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if(row.note.isNotBlank()) Text(row.note, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background).padding(10.dp))
        HorizontalDivider()
        Text(tx("Sistólica (alta)") + ": " + tx(if(row.systolic >= 135) "Sobre la referencia" else "Por debajo de la referencia"), style = MaterialTheme.typography.bodySmall, color = if(row.systolic >= 135) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tx("Diastólica (baja)") + ": " + tx(if(row.diastolic >= 85) "Sobre la referencia" else "Por debajo de la referencia"), style = MaterialTheme.typography.bodySmall, color = if(row.diastolic >= 85) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        if(row.systolic >= 180 || row.diastolic >= 120) Text(tx("Medición muy elevada"), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { explanation = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) { Text(tx("Qué significan estos valores"), style = MaterialTheme.typography.labelMedium) }
        row.medications.forEach { Text(it.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tx("Guardada en el iPhone"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp)); Text("ID ${row.id.take(6)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if(delete != null) IconButton(delete) { Icon(Icons.Default.Delete, tx("Eliminar toma"), tint = MaterialTheme.colorScheme.error) }
        }
    }
    if(explanation) AlertDialog(onDismissRequest = { explanation = false }, title = { Text(tx("Qué significan estos valores")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tx("Referencia orientativa en casa para adultos: sistólica desde 135 o diastólica desde 85 mmHg. Se interpreta el promedio de varios días, no una toma aislada. Si se repite, consúltalo; no cambies tu medicación por tu cuenta. Tus objetivos pueden ser distintos. No se aplica a menores ni al embarazo."))
            if(row.systolic >= 180 || row.diastolic >= 120) Text(tx("Lectura muy elevada. Con dolor de pecho, falta de aire, debilidad o dificultad para hablar, llama a emergencias sin esperar. Sin síntomas, repite tras al menos 1 minuto y, si sigue así, contacta cuanto antes con un profesional sanitario."))
        } }, confirmButton = { TextButton(onClick = { explanation = false }) { Text(tx("Cerrar")) } })
}

@Composable private fun Chart(readings: List<Reading>) {
    val primary = MaterialTheme.colorScheme.primary; val secondary = MaterialTheme.colorScheme.secondary
    val ink = MaterialTheme.colorScheme.onSurface
    val sorted = readings.sortedBy { it.measuredAt }.takeLast(60)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tx("Gráficas"), style = MaterialTheme.typography.headlineSmall)
        Text(tx("Sistólica") + " · " + tx("Diastólica") + " (mmHg)")
        if(sorted.isEmpty()) Text(tx("Todavía no hay tomas")) else {
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val low = 30f; val high = maxOf(160, sorted.maxOf { it.systolic } + 20).toFloat()
                val first = sorted.first().measuredAt; val span = (sorted.last().measuredAt - first).coerceAtLeast(1)
                val left = 38.dp.toPx()
                val labels = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink.toArgb(); textSize = 11.sp.toPx() }
                for(value in 40..high.toInt() step 40) {
                    val y = size.height - 12 - (value-low)/(high-low)*(size.height-24)
                    drawLine(ink.copy(alpha = 0.15f), Offset(left,y), Offset(size.width-12,y), 1f)
                    drawContext.canvas.nativeCanvas.drawText(value.toString(), 0f, y+4f, labels)
                }
                listOf(false to primary, true to secondary).forEach { (diastolic, color) ->
                    val points = sorted.map { r -> Offset(left + (r.measuredAt - first).toFloat() / span * (size.width - left - 12),
                        size.height - 12 - ((if(diastolic) r.diastolic else r.systolic) - low) / (high - low) * (size.height - 24)) }
                    points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 4f) }
                    points.forEach { drawCircle(color, 5f, it) }
                }
            }
            Text("${sorted.first().localTime().toLocalDate()} — ${sorted.last().localTime().toLocalDate()}")
            Text(tx("Sistólica"), color = primary); Text(tx("Diastólica"), color = secondary)
        }
    }
}

private data class Draft(val sys: String = "", val dia: String = "", val pulse: String = "", val note: String = "", val meds: List<Medication> = emptyList())

/** Drafts stay transient; validate every selected row before the single atomic store transaction. */
@Composable private fun AddReading(save: (List<Reading>) -> String?) {
    var count by remember { mutableIntStateOf(1) }; var drafts by remember { mutableStateOf(List(3) { Draft() }) }
    var error by remember { mutableStateOf<String?>(null) }; val focus = LocalFocusManager.current
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(1,3).forEach { n ->
                FilterChip(count == n, { count = n }, label = { Text(n.toString() + " " + tx("Mediciones")) })
            } } }
            items(count) { i ->
                val d = drafts[i]
                fun update(value: Draft) { drafts = drafts.toMutableList().also { it[i] = value } }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tx("Toma %ld").replace("%ld", (i+1).toString()), style = MaterialTheme.typography.titleLarge)
                        Field("Sistólica", d.sys, true) { update(d.copy(sys = it)) }
                        Field("Diastólica", d.dia, true) { update(d.copy(dia = it)) }
                        Field("Pulso", d.pulse, true) { update(d.copy(pulse = it)) }
                        Field("Notas", d.note) { update(d.copy(note = it.take(140))) }
                        d.meds.forEachIndexed { j, m ->
                            fun med(value: Medication) { update(d.copy(meds = d.meds.toMutableList().also { it[j] = value })) }
                            Field("Nombre del medicamento", m.name) { med(m.copy(name = it)) }
                            Field("Dosis (opcional)", m.dose) { med(m.copy(dose = it)) }
                            Field("Notas", m.note) { med(m.copy(note = it)) }
                            TextButton(onClick = { update(d.copy(meds = d.meds.filterIndexed { index, _ -> index != j })) }, modifier = Modifier.focusProperties { canFocus = false }) { Text(tx("Eliminar medicamento")) }
                        }
                        OutlinedButton(onClick = { update(d.copy(meds = d.meds + Medication(name = ""))) }, modifier = Modifier.focusProperties { canFocus = false }) { Text(tx("Añadir medicamento")) }
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
        val context = LocalContext.current
        Button(onClick = {
            focus.clearFocus(); val now = System.currentTimeMillis()
            val rows = drafts.take(count).map { d -> Reading(systolic = integer(d.sys) ?: 0, diastolic = integer(d.dia) ?: 0,
                pulse = if(d.pulse.isBlank()) null else integer(d.pulse) ?: -1, measuredAt = now, note = d.note,
                medications = d.meds.map { it.copy(name = it.name.trim(), dose = it.dose.trim(), note = it.note.trim()) }) }
            error = if(rows.any { !it.valid() }) context.t("Revisa los valores de las tomas.") else save(rows)
        }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).imePadding().heightIn(min = 54.dp)) { Text(tx("Guardar toma")) }
    }
}

/** IME Next plus explicit previous/next controls remove the need to close the numeric keyboard. */
@Composable private fun Field(title: String, value: String, numeric: Boolean = false, change: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(tx(title)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if(numeric) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }))
    if(numeric) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Previous) }, modifier = Modifier.focusProperties { canFocus = false }) { Text(tx("Anterior")) }
        TextButton(onClick = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }, modifier = Modifier.focusProperties { canFocus = false }) { Text(tx("Siguiente")) }
    }
}
