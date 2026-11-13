package com.rafa.hercules.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.rafa.hercules.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')

@Composable
fun PlateCalculator(onClose: () -> Unit) {
    var unit by rememberSaveable { mutableStateOf("kg") }
    var target by rememberSaveable { mutableStateOf("") }
    var bar by rememberSaveable { mutableStateOf("20") }
    val result = remember(target, bar, unit) {
        val t = target.replace(',', '.').toDoubleOrNull()
        val b = bar.replace(',', '.').toDoubleOrNull()
        if (t != null && b != null) calculatePlates(t, b, unit) else null
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Calculadora de discos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kg", "lb").forEach { option ->
                        FilterChip(selected = unit == option, onClick = {
                            if (unit != option) { unit = option; target = ""; bar = if (unit == "kg") "20" else "45" }
                        }, label = { Text(option) })
                    }
                }
                val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(target, { target = it }, label = { Text("Peso total ($unit)") }, singleLine = true, keyboardOptions = keyboard)
                OutlinedTextField(bar, { bar = it }, label = { Text("Peso de la barra ($unit)") }, singleLine = true, keyboardOptions = keyboard)
                Text("Usa tamaños habituales de discos. Verifica que estén disponibles en tu gym.", style = MaterialTheme.typography.bodySmall)
                if (result != null) {
                    Text("Carga total: ${number(result.actualWeight)} $unit")
                    Text(if (result.perSide.isEmpty()) "Solo la barra"
                        else "Por lado: " + result.perSide.entries.joinToString(" + ") { "${it.value} × ${number(it.key)} $unit" })
                    val requested = target.replace(',', '.').toDoubleOrNull()
                    if (requested != null && requested - result.actualWeight > 1e-8)
                        Text("La combinación más cercana sin superar tu objetivo.")
                } else if (target.isNotBlank()) Text("Ingresa pesos válidos; el total debe ser al menos el peso de la barra.")
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } })
}

private fun FinishedWorkout.metrics(): List<String> {
    val seconds = durationMs / 1000
    return listOf(
        "Duración: %02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60),
        "Series de trabajo: ${totals.workSeriesCount}",
        "Ejercicios: ${totals.exerciseCount}",
        "Récords nuevos: ${records.size}"
    )
}
private fun WorkoutRecord.label() = "$exercise: ${number(weight)} $unit × $reps"

@Composable
fun FinishedWorkoutScreen(summary: FinishedWorkout, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Entrenamiento terminado", style = MaterialTheme.typography.headlineSmall)
            Text(summary.name, style = MaterialTheme.typography.titleLarge)
            summary.metrics().forEach { Text(it) }
            summary.records.forEach { Text(it.label()) }
            if (error) Text("No se pudo compartir la imagen.", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !sharing, onClick = {
                sharing = true; error = false
                scope.launch {
                    try {
                        val image = withContext(Dispatchers.IO) { createWorkoutImage(context, summary) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.workoutshare", image)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(context.contentResolver, "Resumen Hércules", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir entrenamiento"))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                    finally { sharing = false }
                }
            }) { Text("Compartir imagen") }
            TextButton(onClick = onClose) { Text("Cerrar") }
        }
    }
}

private fun createWorkoutImage(context: Context, summary: FinishedWorkout): File {
    val width = 900
    val padding = 56
    val heading = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(195, 169, 255); textSize = 52f; typeface = Typeface.DEFAULT_BOLD
    }
    val body = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f
    }
    fun layout(text: String, paint: TextPaint) = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width - padding * 2)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    val featured = summary.records.take(8).map { it.label().take(150) }
    val remaining = summary.records.size - featured.size
    val text = (listOf(summary.name.take(150)) + summary.metrics() + featured +
        if (remaining > 0) listOf("Y $remaining más") else emptyList()).joinToString("\n\n")
    val title = layout("HÉRCULES", heading)
    val details = layout(text, body)
    val bitmap = Bitmap.createBitmap(width, padding * 3 + title.height + details.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(22, 15, 39))
    canvas.translate(padding.toFloat(), padding.toFloat())
    title.draw(canvas)
    canvas.translate(0f, (title.height + padding).toFloat())
    details.draw(canvas)
    val dir = File(context.cacheDir, "workout-share")
    check(dir.isDirectory || dir.mkdirs())
    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    val file = File.createTempFile("hercules-resumen-", ".png", dir)
    try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
    finally { bitmap.recycle() }
    return file
}
