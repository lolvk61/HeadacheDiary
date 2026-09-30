package com.headachediary.app.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.MedHelp
import com.headachediary.app.data.SHORT_SLEEP_MINUTES
import com.headachediary.app.data.computeStats
import com.headachediary.app.data.hasSharpPressureChange
import com.headachediary.app.data.symptomLabels
import com.headachediary.app.data.triggerLabels
import com.headachediary.app.ui.DAY_MS
import com.headachediary.app.ui.appLocale
import com.headachediary.app.ui.formatDate
import com.headachediary.app.ui.formatDuration
import com.headachediary.app.ui.formatPressure
import com.headachediary.app.ui.formatPressureChange
import com.headachediary.app.ui.formatTemperature
import com.headachediary.app.ui.formatTime
import com.headachediary.app.ui.intensityLabelRes
import com.headachediary.app.ui.painColor
import com.headachediary.app.ui.toLocalDate
import com.headachediary.app.ui.weatherLabelRes
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * PDF-отчёт для врача: короткая сводка за период и хронологический список приступов
 * обычным языком, без кодов и таблиц с разделителями.
 */
object DoctorReport {
    private const val MAX_NOTES = 600

    /** Записи за период (null — за всё время) в хронологическом порядке. */
    fun select(entries: List<HeadacheEntry>, days: Int?, now: Long = System.currentTimeMillis()): List<HeadacheEntry> =
        entries.filter { days == null || it.startTime >= now - days * DAY_MS }.sortedBy { it.startTime }

    fun build(context: Context, allEntries: List<HeadacheEntry>, painFreeDays: Set<Long>, days: Int?): File {
        val now = System.currentTimeMillis()
        val list = select(allEntries, days, now)
        require(list.isNotEmpty()) { "No entries in the selected period" }

        val locale = context.appLocale()
        val today = now.toLocalDate()
        val from = if (days == null) list.first().startTime.toLocalDate() else today.minusDays(days.toLong())
        val periodDays = ChronoUnit.DAYS.between(from, today).toInt() + 1
        val stats = computeStats(list)
        val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy, EEE", locale)

        val titlePaint = paint(22f, bold = true, color = 0xFF3F3272.toInt())
        val headingPaint = paint(15f, bold = true, color = 0xFF3F3272.toInt())
        val bodyPaint = paint(12f)
        val boldPaint = paint(12f, bold = true)
        val mutedPaint = paint(10f, color = 0xFF666666.toInt())

        val doc = PdfDocument()
        val w = PdfWriter(doc, context.getString(R.string.report_page))

        w.paragraph(context.getString(R.string.report_title), titlePaint, after = 6f)
        w.paragraph(
            context.getString(R.string.report_period, formatDate(context, from), formatDate(context, today), periodDays),
            bodyPaint,
        )
        w.paragraph(context.getString(R.string.report_generated, formatDate(context, today)), mutedPaint, after = 10f)
        w.rule()

        w.paragraph(context.getString(R.string.report_summary), headingPaint, after = 6f)
        fun bullet(text: String) = w.paragraph("•  $text", bodyPaint, after = 3f, indent = 6f)
        bullet(context.getString(R.string.report_pain_days, stats.painDays, periodDays))
        val markedPainFree = painFreeDays.count { it >= from.toEpochDay() && it <= today.toEpochDay() }
        if (markedPainFree > 0) bullet(context.getString(R.string.report_pain_free_days, markedPainFree))
        bullet(context.getString(R.string.report_attacks, stats.attacks))
        bullet(context.getString(R.string.report_migraine_days, stats.migraineDays))
        bullet(context.getString(R.string.report_tension_days, stats.tensionDays))
        stats.avgIntensity?.let {
            bullet(context.getString(R.string.report_avg_intensity, String.format(locale, "%.1f", it)))
        }
        stats.avgDurationMs?.let {
            bullet(context.getString(R.string.report_avg_duration, formatDuration(context, it)))
        }
        bullet(context.getString(R.string.report_med_days, stats.medDays))
        if (stats.topTriggers.isNotEmpty()) {
            bullet(
                context.getString(
                    R.string.report_frequent_triggers,
                    stats.topTriggers.joinToString(", ") { (t, n) -> "${context.getString(t.labelRes)} ($n)" },
                ),
            )
        }
        if (stats.topSymptoms.isNotEmpty()) {
            bullet(
                context.getString(
                    R.string.report_frequent_symptoms,
                    stats.topSymptoms.joinToString(", ") { (s, n) -> "${context.getString(s.labelRes)} ($n)" },
                ),
            )
        }
        val withPressure = list.filter { it.pressureChange3h != null }
        if (withPressure.isNotEmpty()) {
            bullet(
                context.getString(
                    R.string.report_sharp_pressure,
                    withPressure.count { it.hasSharpPressureChange() },
                    withPressure.size,
                ),
            )
        }
        val sleeps = list.mapNotNull { it.sleepMinutes }
        if (sleeps.isNotEmpty()) {
            bullet(
                context.getString(
                    R.string.report_avg_sleep,
                    formatDuration(context, sleeps.average().toLong() * 60_000L),
                ),
            )
            bullet(
                context.getString(
                    R.string.report_short_sleep,
                    sleeps.count { it < SHORT_SLEEP_MINUTES },
                    sleeps.size,
                ),
            )
        }
        val medDaysLast30 = computeStats(select(allEntries, 30, now)).medDays
        if (medDaysLast30 >= 10) {
            w.spacer(4f)
            w.paragraph(context.getString(R.string.report_med_note), boldPaint, after = 4f)
        }

        w.spacer(8f)
        w.rule()
        w.paragraph(context.getString(R.string.report_episodes), headingPaint, after = 8f)

        list.forEach { e ->
            val timeText = e.endTime
                ?.let { "${formatTime(e.startTime)} – ${formatTime(it)} (${formatDuration(context, it - e.startTime)})" }
                ?: context.getString(R.string.report_end_unknown, formatTime(e.startTime))
            val type = context.getString(HeadacheType.fromKey(e.type).labelRes)
            val intensityText = e.intensity?.let {
                context.getString(
                    R.string.report_intensity,
                    it,
                    context.getString(intensityLabelRes(it)).lowercase(locale),
                )
            } ?: context.getString(R.string.report_intensity_unknown)

            val lines = mutableListOf<Pair<CharSequence, TextPaint>>()
            lines += "${dayFmt.format(e.startTime.toLocalDate())}, $timeText" to boldPaint
            lines += "$type · $intensityText" to bodyPaint
            weatherLine(context, e)?.let { lines += context.getString(R.string.report_weather, it) to bodyPaint }
            healthLine(context, e)?.let { lines += context.getString(R.string.report_health, it) to bodyPaint }
            e.symptoms.symptomLabels(context).takeIf { it.isNotEmpty() }?.let {
                lines += context.getString(R.string.report_symptoms, it.joinToString(", ")) to bodyPaint
            }
            e.triggers.triggerLabels(context).takeIf { it.isNotEmpty() }?.let {
                lines += context.getString(R.string.report_triggers, it.joinToString(", ")) to bodyPaint
            }
            if (e.medication.isNotBlank()) {
                val help = MedHelp.entries.firstOrNull { it.name == e.medicationHelped }
                    ?.let { " — ${context.getString(it.labelRes).lowercase(locale)}" }.orEmpty()
                lines += context.getString(R.string.report_medication, e.medication) + help to bodyPaint
            }
            if (e.notes.isNotBlank()) {
                val notes = if (e.notes.length > MAX_NOTES) e.notes.take(MAX_NOTES) + "…" else e.notes
                lines += context.getString(R.string.report_notes, notes) to bodyPaint
            }
            w.block(lines, painColor(e.intensity).toArgb())
        }

        w.spacer(6f)
        w.paragraph(context.getString(R.string.report_disclaimer), mutedPaint)
        w.finish()

        val dir = File(context.cacheDir, "reports").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "headache-diary-$today.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** «+12 °C, пасмурно, давление 758 мм рт. ст. (за 3 ч: −2,3 мм рт. ст.)» или null, если погоды нет. */
    private fun weatherLine(context: Context, e: HeadacheEntry): String? {
        val parts = mutableListOf<String>()
        e.temperature?.let { parts += formatTemperature(context, it) }
        e.weatherCode?.let { parts += context.getString(weatherLabelRes(it)).lowercase(context.appLocale()) }
        e.pressure?.let { pressure ->
            var text = context.getString(R.string.report_pressure, formatPressure(context, pressure))
            e.pressureChange3h?.let { change ->
                text += " (${context.getString(R.string.weather_change_inline, formatPressureChange(context, change))})"
            }
            parts += text
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    /** «сон перед приступом 9 ч 50 мин, шагов за сутки до приступа: 3 200, пульс в покое: 62» или null. */
    private fun healthLine(context: Context, e: HeadacheEntry): String? {
        val parts = mutableListOf<String>()
        e.sleepMinutes?.let {
            parts += context.getString(R.string.report_sleep, formatDuration(context, it * 60_000L))
        }
        e.steps24h?.let {
            parts += context.getString(R.string.report_steps, String.format(context.appLocale(), "%,d", it))
        }
        e.restingHeartRate?.let { parts += context.getString(R.string.report_resting_hr, it) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val title = context.getString(R.string.report_title)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share)))
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }
}

/** Простая вёрстка текста на страницах A4 с переносом строк и автоматическим переходом на новую страницу. */
private class PdfWriter(private val doc: PdfDocument, private val pageLabel: String) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 40f
    private val contentW = pageW - 2 * margin

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNo = 0
    private var y = 0f

    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = 0xFF888888.toInt()
    }
    private val rulePaint = Paint().apply {
        color = 0xFFCCCCCC.toInt()
        strokeWidth = 1f
    }

    private fun newPage() {
        closePage()
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        page = p
        canvas = p.canvas
        y = margin
    }

    private fun closePage() {
        val p = page ?: return
        canvas?.drawText(pageLabel.format(pageNo), margin, pageH - 20f, footerPaint)
        doc.finishPage(p)
        page = null
        canvas = null
    }

    fun finish() = closePage()

    private fun needs(height: Float) {
        if (page == null || y + height > pageH - margin) newPage()
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()

    private fun draw(layout: StaticLayout, indent: Float) {
        val c = canvas ?: return
        c.save()
        c.translate(margin + indent, y)
        layout.draw(c)
        c.restore()
    }

    fun paragraph(text: CharSequence, paint: TextPaint, after: Float = 4f, indent: Float = 0f) {
        val l = layout(text, paint, (contentW - indent).toInt())
        needs(l.height.toFloat())
        draw(l, indent)
        y += l.height + after
    }

    fun spacer(height: Float) {
        needs(height)
        y += height
    }

    fun rule() {
        needs(10f)
        canvas?.drawLine(margin, y, pageW - margin, y, rulePaint)
        y += 10f
    }

    /** Блок из нескольких строк с цветной полосой слева; не разрывается между страницами. */
    fun block(lines: List<Pair<CharSequence, TextPaint>>, accent: Int) {
        val layouts = lines.map { (text, paint) -> layout(text, paint, (contentW - 12f).toInt()) }
        val total = layouts.sumOf { it.height }.toFloat() + 3f * (layouts.size - 1)
        needs(total)
        canvas?.drawRect(margin, y, margin + 4f, y + total, Paint().apply { color = accent })
        layouts.forEach { l ->
            draw(l, 12f)
            y += l.height + 3f
        }
        y += 6f
    }
}
