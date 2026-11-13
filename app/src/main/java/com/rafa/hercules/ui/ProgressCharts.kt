package com.rafa.hercules.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rafa.hercules.data.SessionWithSeries
import com.rafa.hercules.data.seriesWeightKg

@Composable
fun ProgressCharts(sessions: List<SessionWithSeries>, cardioExerciseIds: Set<String> = emptySet()) {
    // Evita una curva que mezcle pecho, pierna u otras composiciones.
    Text("Consulta el progreso por ejercicio o compara una rutina equivalente.", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun LineChart(data: List<Float?>, maxVal: Float, lineColor: Color) {
    if (data.isEmpty()) return
    
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp)) {
        val width = size.width
        val height = size.height
        
        val stepX = if (data.size > 1) width / (data.size - 1) else width
        
        val path = Path()
        var connected = false
        data.forEachIndexed { index, value ->
            if (value == null || !value.isFinite()) { connected = false; return@forEachIndexed }
            val x = index * stepX
            val y = height - ((value / maxVal.takeIf { it.isFinite() && it > 0f }.let { it ?: 1f }) * height)
            
            if (!connected) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            
            connected = true
            // Dibujar punto
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }
        
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
