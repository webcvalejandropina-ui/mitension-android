package com.example.mitension.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.mitension.model.*
import com.example.mitension.t
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A local A4 report retains all readings and wraps long notes across pages rather than truncating. */
object PdfReport {
    fun make(context: Context, readings: List<Reading>): File {
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "Informe-Mi-Tension-${java.util.UUID.randomUUID()}.pdf")
        val doc = PdfDocument(); var number = 0; var page: PdfDocument.Page? = null; var y = 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
        fun next() { page?.let { doc.finishPage(it) }; number++; page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 42f }
        fun line(text: String, size: Float = 11f) {
            paint.textSize = size
            text.split('\n').forEach { paragraph ->
                var remainder = paragraph
                do {
                    if(page == null || y > 792) next()
                    val count = if(remainder.isEmpty()) 0 else paint.breakText(remainder, true, 510f, null).coerceAtLeast(1)
                    page!!.canvas.drawText(remainder.take(count), 42f, y, paint); y += size + 7
                    remainder = remainder.drop(count)
                } while(remainder.isNotEmpty())
            }
        }
        try {
            line("Mi Tensión", 22f)
            grouped(readings).forEach { (day, periods) ->
                line(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), 15f)
                periods.forEach { (period, rows) ->
                    line(context.t(if(period == "morning") "Mañana" else "Noche"), 13f)
                    rows.forEach { r ->
                        line("${r.systolic} / ${r.diastolic} mmHg · ${r.localTime().toLocalTime()} · ID ${r.id.take(6)}", 12f)
                        r.pulse?.let { line("$it ${context.t("lpm")}") }
                        if(r.note.isNotBlank()) line(r.note)
                        r.medications.forEach { line(it.description) }; y += 10
                    }
                }
            }
            line(context.t("Las mediciones han sido introducidas por el usuario. Este informe no sustituye la valoración clínica."), 9f)
            page?.let { doc.finishPage(it) }
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
        return file
    }
}
