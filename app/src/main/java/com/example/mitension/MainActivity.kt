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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
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
    val colors = if (dark) darkColorScheme(primary = Color(0xFF77D8C1), background = Color(0xFF0C1920),
        surface = Color(0xFF142731), secondary = Color(0xFFCFB5FF))
    else lightColorScheme(primary = Color(0xFF087F6D), background = Color(0xFFF3F8F7),
        surface = Color.White, secondary = Color(0xFF715196))
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
    val context = LocalContext.current
    val readings by context.store.readings.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
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
    fun share(excel: Boolean) { scope.launch {
        runCatching {
            val file = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "exports").apply { mkdirs() }
                if (excel) File(directory, "Mi-Tension-${java.util.UUID.randomUUID()}.xlsx").apply {
                    writeBytes(Excel.write(readings, listOf(context.t("Fecha y hora"), context.t("Momento"), context.t("Sistólica"),
                        context.t("Diastólica"), context.t("Pulso"), context.t("Notas"), "ID", context.t("Medicamentos de esta toma")),
                        periods = context.t("Mañana") to context.t("Noche")))
                } else PdfReport.make(context, readings)
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).setType(if (excel) "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("Mi Tension", uri) }
            context.startActivity(Intent.createChooser(intent, context.t("Compartir o imprimir")))
        }.onFailure { error = context.t("No se pudo exportar el archivo.") }
    } }
    val title = when(route) { "add" -> "Guardar nueva toma"; "more" -> "Cuida tu rutina"; "guide" -> "Guía y privacidad"
        "reminders" -> "Alertas"; "import" -> "Importar registros de Excel"; else -> "Mi Tensión" }
    Scaffold(topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (route == "home") Image(painterResource(R.drawable.pineapple_mark), null, Modifier.size(36.dp))
        Text(tx(title), style = MaterialTheme.typography.titleLarge)
    } }, navigationIcon = { if(route != "home") IconButton(onClick = { route = "home" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tx("Cerrar")) } },
        actions = { if(route == "home") { IconButton(onClick = { route = "reminders" }) { Icon(Icons.Default.Notifications, tx("Alertas")) }
            IconButton(onClick = { route = "more" }) { Icon(Icons.Default.MoreVert, tx("Más")) } } }) },
        snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when(route) {
                "home" -> Home(readings, { route = "add" }, { share(false) }, {
                    runCatching { ReportPrinter.print(context, readings) }.onFailure { error = context.t("No se pudo exportar el archivo.") }
                }, { reading ->
                    runCatching { context.store.delete(reading.id); Reminders.reconcile(context) }.onFailure { error = context.t("No se pudo guardar la toma en este iPhone. Inténtalo de nuevo.") }
                })
                "add" -> AddReading { rows ->
                    try { context.store.add(rows); Reminders.reconcile(context); route = "home"; null }
                    catch (e: Exception) { context.t("No se pudo guardar la toma en este iPhone. Inténtalo de nuevo.") }
                }
                "more" -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(tx("Guía, avisos y tus datos, en un solo lugar."), style = MaterialTheme.typography.bodyLarge)
                    Tool("Alertas", Icons.Default.Notifications) { route = "reminders" }
                    Tool("Guía y privacidad", Icons.Default.Info) { route = "guide" }
                    Tool("Exportar a Excel", Icons.Default.Share) { share(true) }
                    Tool("Importar registros de Excel", Icons.Default.Add) { route = "import" }
                    Text(tx("La copia contiene datos de salud. Guárdala en un lugar privado y compártela solo con personas de confianza."))
                }
                "guide" -> Guide()
                "reminders" -> ReminderSettings()
                "import" -> ImportInstructions { launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }
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
    FilledTonalButton(action, Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(18.dp)) {
        Icon(icon, null); Spacer(Modifier.width(12.dp)); Text(tx(title))
    }
}

/** Swipe navigation is native pager state; menu taps and swipes share the same selected page. */
@Composable private fun Home(readings: List<Reading>, add: () -> Unit, share: () -> Unit, print: () -> Unit, delete: (Reading) -> Unit) {
    val clock = LocalClockVersion.current
    val days = remember(readings, clock) { grouped(readings) }
    val pager = rememberPagerState { 4 }; val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Reading?>(null) }
    Column {
        ScrollableTabRow(pager.currentPage, edgePadding = 8.dp) {
            listOf("Resumen", "Histórico", "Gráficas", "Médico").forEachIndexed { i, title ->
                Tab(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(tx(title)) })
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if(page == 0) {
                    item { LocalNotice() }
                    readings.firstOrNull()?.let { last -> item { ReadingCard(last, null) } }
                    item { Text(tx("Registro de tomas"), style = MaterialTheme.typography.titleLarge) }
                }
                if(page == 2) item { Chart(readings) }
                else {
                    if(page == 3) item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(share, Modifier.fillMaxWidth()) { Text(tx("Compartir o imprimir")) }
                        OutlinedButton(print, Modifier.fillMaxWidth()) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text(tx("Imprimir")) }
                    } }
                    if(readings.isEmpty()) item { Text(tx("Todavía no hay tomas")) }
                    days.forEach { (day, periods) ->
                        item(key = day.toString()) {
                            Text(day.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            periods.forEach { (period, rows) ->
                                Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(tx(if(period == "morning") "Mañana" else "Noche"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        rows.forEach { row -> ReadingCard(row, { pending = row }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(pager.currentPage == 0) Button(add, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).heightIn(min = 54.dp)) { Text(tx("Guardar nueva toma")) }
    }
    pending?.let { row -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(tx("Eliminar toma")) },
        text = { Text(tx("La toma se borrará del almacenamiento local del iPhone.")) },
        confirmButton = { TextButton(onClick = { delete(row); pending = null }) { Text(tx("Eliminar toma")) } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(tx("Cancelar")) } }) }
}

@Composable private fun LocalNotice() {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(!prefs.getBoolean("noticeDismissed", false)) }
    if(visible) Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tx("Guardado en este iPhone"), Modifier.weight(1f))
            IconButton(onClick = { prefs.edit().putBoolean("noticeDismissed", true).apply(); visible = false }) { Icon(Icons.Default.Close, tx("Cerrar")) }
        }
    }
}

@Composable private fun ReadingCard(row: Reading, delete: (() -> Unit)?) {
    val context = LocalContext.current
    var explanation by remember { mutableStateOf(false) }
    val above = row.systolic >= 135 || row.diastolic >= 85
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.systolic} / ${row.diastolic}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text("mmHg"); if(delete != null) IconButton(delete) { Icon(Icons.Default.Delete, tx("Eliminar toma")) }
        }
        Text("${row.time()} · ${row.localTime().toLocalDate()}" + (row.pulse?.let { " · $it ${tx("lpm")}" } ?: ""))
        Text(tx("Sistólica (alta)") + ": " + tx(if(row.systolic >= 135) "Sobre la referencia" else "Por debajo de la referencia"), color = if(above) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        Text(tx("Diastólica (baja)") + ": " + tx(if(row.diastolic >= 85) "Sobre la referencia" else "Por debajo de la referencia"), color = if(above) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        if(row.systolic >= 180 || row.diastolic >= 120) Text(tx("Medición muy elevada"), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { explanation = true }) { Text(tx("Qué significan estos valores")) }
        if(row.note.isNotBlank()) Text(row.note)
        row.medications.forEach { Text(it.description, style = MaterialTheme.typography.bodyMedium) }
        Text("ID ${row.id.take(6)}", style = MaterialTheme.typography.labelSmall)
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
