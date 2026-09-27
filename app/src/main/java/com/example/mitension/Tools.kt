package com.example.mitension

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import com.example.mitension.model.*
import com.example.mitension.reminders.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Clinical instructions are orientative and explicitly reference an external, validated upper-arm device. */
@Composable internal fun Guide() {
    val context = LocalContext.current; var image by remember { mutableStateOf<Int?>(null) }
    val steps = listOf(
        "Prepara el tensiómetro" to "Usa un tensiómetro validado de brazo y un manguito de tu talla. La app registra las lecturas del aparato.",
        "Antes de medir" to "Evita ejercicio, tabaco y cafeína durante los 30 minutos previos. Vacía la vejiga.",
        "Descansa 5 minutos" to "Siéntate en silencio, con la espalda apoyada, los pies en el suelo y las piernas sin cruzar.",
        "Coloca el brazo" to "Pon el manguito sobre la piel, siguiendo las instrucciones del aparato. Apoya el brazo a la altura del corazón.",
        "Haz dos tomas" to "No hables ni te muevas. Espera al menos 1 minuto entre las dos mediciones y guarda cada una por separado.",
        "Sigue tu pauta" to "Mide en los horarios indicados por tu profesional sanitario y lleva el informe a la consulta.")
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(steps.size) { index ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${index+1}. ${tx(steps[index].first)}", style = MaterialTheme.typography.titleMedium)
                    Text(tx(steps[index].second))
                    val asset = when(index) { 2 -> R.drawable.guide_posture; 3 -> R.drawable.guide_cuff; else -> null }
                    if(asset != null) {
                        Image(painterResource(asset), tx(steps[index].first), Modifier.fillMaxWidth().clickable { image = asset })
                        Text(tx("Toca para ampliar la imagen."), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/monitoring-your-blood-pressure-at-home"))) }) { Text(tx("Fuente: American Heart Association")) } }
        item { Text(tx("Tu privacidad"), style = MaterialTheme.typography.titleLarge) }
        item { Text(tx("No guardamos datos en servidores")) }
        item { Text(tx("Tus tomas y notas se guardan únicamente en el almacenamiento local de este iPhone para que puedas consultar el histórico. La app no exige una cuenta ni envía tus mediciones a nuestros servidores.")) }
        item { Text(tx("Eliminar la app puede borrar el histórico local. Exporta una copia si quieres conservarlo.")) }
        item { Text(tx("Las referencias son orientativas para adultos, no un diagnóstico ni un objetivo personal. No se aplican al embarazo ni a menores.")) }
    }
    image?.let { asset -> Dialog(onDismissRequest = { image = null }) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val transform = rememberTransformableState { scale, offset, _ -> zoom = (zoom*scale).coerceIn(1f,4f); pan += offset }
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp)) {
            Box(Modifier.fillMaxWidth().height(320.dp).clipToBounds().transformable(transform)) {
                Image(painterResource(asset), tx("Cómo tomar la tensión"), Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.Center)
                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y))
            }
            TextButton(onClick = { image = null }) { Text(tx("Cerrar")) }
        } }
    } }
}

/** All positions and required columns match the iOS workbook, including stable UUIDs and hidden metadata. */
@Composable internal fun ImportInstructions(import: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Button(import, Modifier.fillMaxWidth()) { Text(tx("Importar registros de Excel")) } }
        item { Text(tx("Formato del archivo"), style = MaterialTheme.typography.titleLarge) }
        item { Text(tx("Usa el Excel (.xlsx) exportado por Mi Tensión como plantilla. Conserva la primera fila y el orden de las columnas; no uses fórmulas.")) }
        item { Text(tx("Cada fila desde la segunda es una toma independiente. Puedes importar 1, 2 o 3 tomas de mañana o noche por día; no se promedian ni se exige completar tres.")) }
        item { Text(tx("Mañana y noche se calculan por la fecha y hora local: antes de las 14:00 es mañana; desde las 14:00 es noche. La columna B no cambia esa clasificación.")) }
        item { Text(tx("Columnas"), style = MaterialTheme.typography.titleLarge) }
        val titles = listOf("Fecha y hora", "Momento", "Sistólica", "Diastólica", "Pulso", "Notas", "ID", "Medicamentos de esta toma")
        items(titles.size) { index -> Card(Modifier.fillMaxWidth()) { Text("${'A'+index} · ${tx(titles[index])}", Modifier.padding(14.dp)) } }
        item { Text(tx("A, C, D y G son obligatorias. A debe ser una fecha y hora de Excel, no texto; C y D son números enteros sin unidades. E, F y H pueden quedar vacías.")) }
        item { Text(tx("G identifica cada toma: conserva su UUID para evitar duplicados y usa un UUID distinto para una toma nueva. I, J y K son columnas técnicas ocultas; no las modifiques ni copies sus valores a tomas nuevas.")) }
    }
}

/** Save configuration first; denied permissions must not silently discard the user's chosen routine. */
@Composable internal fun ReminderSettings() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(Reminders.load(context)) }; val latest by rememberUpdatedState(config)
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        message = context.t(if(it) "Configuración guardada" else "Activa las notificaciones en Ajustes para recibir avisos.")
    }
    DisposableEffect(Unit) { onDispose { if(latest.morning.valid() && latest.evening.valid()) runCatching { Reminders.save(context, latest) } } }
    Column {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ScheduleEditor("Mañana", config.morning) { config = config.copy(morning = it) } }
            item { ScheduleEditor("Noche", config.evening) { config = config.copy(evening = it) } }
            item { Text(tx("A los 30 minutos volveremos a avisarte si no has guardado la toma de ese día y periodo.")) }
            item { Text(tx("Sin permiso de alarmas exactas, Android puede retrasar los avisos. Las alarmas sonoras necesitan ese permiso.")) }
            item { OutlinedButton(onClick = {
                if(Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text(tx("Permiso de alarmas exactas")) } }
            item { OutlinedButton(onClick = { context.stopService(Intent(context, AlarmSoundService::class.java)) }) { Text(tx("Detener alarma")) } }
            message?.let { item { Text(it) } }
        }
        Button(onClick = {
            runCatching {
                Reminders.save(context, config)
                message = context.t("Configuración guardada")
                if((config.morning.enabled || config.evening.enabled) && Build.VERSION.SDK_INT >= 33 && !Reminders.notificationsAllowed(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }.onFailure { message = context.t("Selecciona al menos un día.") }
        }, Modifier.fillMaxWidth().padding(20.dp)) { Text(tx("Guardar")) }
    }
}

@Composable private fun ScheduleEditor(title: String, schedule: Schedule, change: (Schedule) -> Unit) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(tx(title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Switch(schedule.enabled, { change(schedule.copy(enabled = it)) })
            }
            OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute -> change(schedule.copy(hour = hour, minute = minute)) }, schedule.hour, schedule.minute, android.text.format.DateFormat.is24HourFormat(context)).show() }) {
                Text("%02d:%02d".format(schedule.hour, schedule.minute))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day -> FilterChip(day in schedule.weekdays, {
                    change(schedule.copy(weekdays = if(day in schedule.weekdays) schedule.weekdays - day else schedule.weekdays + day))
                }, label = { Text(DayOfWeek.of(if(day == 1) 7 else day-1).getDisplayName(TextStyle.SHORT, Locale.getDefault())) }) }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(tx("Alarma sonora"), Modifier.weight(1f)); Switch(schedule.alarm, { change(schedule.copy(alarm = it)) })
            }
        }
    }
}
