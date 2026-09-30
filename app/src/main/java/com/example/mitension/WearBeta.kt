package com.example.mitension

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.mitension.model.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

/** A preview exists only for provider-labelled watch data; missing provenance is not guessed. */
internal data class WatchPressure(val sourceId: String, val systolic: Int, val diastolic: Int,
    val measuredAt: Long, val origin: String) {
    fun toReading(): Reading = Reading(
        id = UUID.nameUUIDFromBytes("health-connect-watch:$origin:$sourceId".toByteArray(StandardCharsets.UTF_8)).toString(),
        systolic = systolic, diastolic = diastolic, measuredAt = measuredAt,
        note = "Wear OS · Health Connect (beta)"
    )
}
internal data class WatchPulse(val bpm: Long, val measuredAt: Long, val origin: String)
internal data class WatchPreview(val pulse: WatchPulse?, val pressures: List<WatchPressure>)

/** Never present phone, unknown-origin or manually entered records as watch measurements. */
internal fun isWatchDevice(type: Int?): Boolean = type == Device.TYPE_WATCH

/** Health Connect's permissions screen opens this local explanation on Android 13–14+. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiTensionTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.wear_beta_badge), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.wear_privacy_rationale), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.wear_no_estimate), style = MaterialTheme.typography.bodyMedium)
            }
        } }
    }
}

/** Read-only, foreground-only Health Connect adapter; no Bluetooth scan or Samsung-only SDK. */
internal object WatchHealthReader {
    val heartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    val pressurePermission = HealthPermission.getReadPermission(BloodPressureRecord::class)
    val permissions = setOf(heartPermission, pressurePermission)

    fun available(context: Context) = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun preview(context: Context): WatchPreview {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val end = Instant.now()
        var pulse: WatchPulse? = null
        val pressures = mutableListOf<WatchPressure>()
        if (heartPermission in granted) {
            var token: String? = null
            // Keep the preview bounded; it is not a background history import.
            for (attempt in 0 until 8) {
                val page = client.readRecords(ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(end.minus(1, ChronoUnit.DAYS), end),
                    pageToken = token
                ))
                page.records.filter { isWatchDevice(it.metadata.device?.type) }.forEach { record ->
                    record.samples.forEach { sample ->
                        if (pulse == null || sample.time.toEpochMilli() > pulse!!.measuredAt)
                            pulse = WatchPulse(sample.beatsPerMinute, sample.time.toEpochMilli(), record.metadata.dataOrigin.packageName)
                    }
                }
                token = page.pageToken
                if (token.isNullOrEmpty()) break
            }
        }
        if (pressurePermission in granted) {
            var token: String? = null
            for (attempt in 0 until 8) {
                val page = client.readRecords(ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(end.minus(7, ChronoUnit.DAYS), end),
                    pageToken = token
                ))
                page.records.filter { isWatchDevice(it.metadata.device?.type) }.forEach { record ->
                    val candidate = WatchPressure(record.metadata.id,
                        record.systolic.inMillimetersOfMercury.roundToInt(),
                        record.diastolic.inMillimetersOfMercury.roundToInt(),
                        record.time.toEpochMilli(), record.metadata.dataOrigin.packageName)
                    if (candidate.toReading().valid()) pressures += candidate
                }
                token = page.pageToken
                if (token.isNullOrEmpty()) break
            }
        }
        return WatchPreview(pulse, pressures.distinctBy { it.origin to it.sourceId }.sortedByDescending { it.measuredAt })
    }
}

/** Optional beta surface never creates a reading without a watch BP record and user confirmation. */
@Composable internal fun WearBetaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<WatchPreview?>(null) }
    var status by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WatchPressure?>(null) }
    fun refresh() { scope.launch {
        busy = true
        runCatching { withContext(Dispatchers.IO) { WatchHealthReader.preview(context) } }
            .onSuccess { preview = it; status = null }
            .onFailure { status = R.string.wear_error }
        busy = false
    } }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isEmpty()) status = R.string.wear_permission_denied else refresh()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.wear_intro), style = MaterialTheme.typography.bodyLarge) }
        item { Text(stringResource(R.string.wear_no_estimate), style = MaterialTheme.typography.bodyMedium) }
        item { Text(stringResource(R.string.wear_requirements), style = MaterialTheme.typography.bodySmall) }
        item {
            Button(onClick = {
                if (!WatchHealthReader.available(context)) status = R.string.wear_unavailable
                else scope.launch {
                    runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
                        .onSuccess { granted -> if (granted.containsAll(WatchHealthReader.permissions)) refresh()
                            else permissionLauncher.launch(WatchHealthReader.permissions) }
                        .onFailure { status = R.string.wear_error }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wear_connect)) }
        }
        status?.let { id -> item { Text(stringResource(id), color = MaterialTheme.colorScheme.error) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        preview?.let { data ->
            item { Text(stringResource(R.string.wear_pulse_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Text(data.pulse?.let { "${it.bpm} ${context.t("lpm")} · ${Instant.ofEpochMilli(it.measuredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}" }
                ?: stringResource(R.string.wear_no_pulse)) }
            item { Text(stringResource(R.string.wear_bp_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (data.pressures.isEmpty()) item { Text(stringResource(R.string.wear_no_bp)) }
            data.pressures.take(20).forEach { value -> item(key = value.origin + value.sourceId) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${value.systolic} / ${value.diastolic} mmHg", style = MaterialTheme.typography.titleLarge)
                    Text("${Instant.ofEpochMilli(value.measuredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()} · ${value.origin}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { selected = value }) { Text(stringResource(R.string.wear_save)) }
                } }
            } }
        }
        item { Text(stringResource(R.string.wear_manual), style = MaterialTheme.typography.bodySmall) }
    }
    selected?.let { item -> AlertDialog(onDismissRequest = { selected = null },
        title = { Text(stringResource(R.string.wear_confirm_title)) },
        text = { Text(stringResource(R.string.wear_confirm_body, item.systolic, item.diastolic)) },
        confirmButton = { TextButton(onClick = {
            runCatching { context.store.importRows(listOf(item.toReading())) }
                .onSuccess { status = if (it == 0) R.string.wear_duplicate else R.string.wear_saved }
                .onFailure { status = R.string.wear_error }
            selected = null
        }) { Text(stringResource(R.string.wear_save)) } },
        dismissButton = { TextButton(onClick = { selected = null }) { Text(context.t("Cancelar")) } }) }
}
