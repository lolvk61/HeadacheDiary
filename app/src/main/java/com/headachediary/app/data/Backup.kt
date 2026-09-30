package com.headachediary.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Содержимое резервной копии: приступы и дни, отмеченные как «без боли». */
data class BackupData(val entries: List<HeadacheEntry>, val painFreeDays: List<Long>)

/** Резервная копия в формате JSON: переносится между телефонами и читается человеком. */
object Backup {
    private const val APP_ID = "HeadacheDiary"
    private const val FORMAT_VERSION = 4
    private const val MAX_TEXT = 5_000

    fun toJson(
        entries: List<HeadacheEntry>,
        painFreeDays: List<Long>,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val array = JSONArray()
        entries.sortedBy { it.startTime }.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("startTime", e.startTime)
                    e.endTime?.let { put("endTime", it) }
                    put("type", e.type)
                    e.intensity?.let { put("intensity", it) }
                    put("symptoms", e.symptoms)
                    put("triggers", e.triggers)
                    put("medication", e.medication)
                    put("medicationHelped", e.medicationHelped)
                    put("notes", e.notes)
                    e.temperature?.let { put("temperature", it) }
                    e.pressure?.let { put("pressure", it) }
                    e.pressureChange3h?.let { put("pressureChange3h", it) }
                    e.pressureChange24h?.let { put("pressureChange24h", it) }
                    e.humidity?.let { put("humidity", it) }
                    e.weatherCode?.let { put("weatherCode", it) }
                    e.sleepMinutes?.let { put("sleepMinutes", it) }
                    e.steps24h?.let { put("steps24h", it) }
                    e.restingHeartRate?.let { put("restingHeartRate", it) }
                },
            )
        }
        val days = JSONArray()
        painFreeDays.sorted().forEach { days.put(it) }
        return JSONObject()
            .put("app", APP_ID)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("entries", array)
            .put("painFreeDays", days)
            .toString(2)
    }

    /** Число из поля JSON или null, если поля нет или в нём не число. */
    private fun JSONObject.optNumber(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name, Double.NaN).takeIf { !it.isNaN() } else null

    /** Разбирает файл резервной копии. Бросает исключение, если файл не от этого приложения. */
    fun parse(text: String): BackupData {
        val root = JSONObject(text)
        require(root.optString("app") == APP_ID) { "Not a Headache Diary backup" }
        val array = root.getJSONArray("entries")
        val knownSymptoms = Symptom.entries.map { it.name }.toSet()
        val knownTriggers = Trigger.entries.map { it.name }.toSet()

        val entries = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            HeadacheEntry(
                startTime = o.getLong("startTime"),
                endTime = if (o.has("endTime") && !o.isNull("endTime")) o.getLong("endTime") else null,
                type = HeadacheType.fromKey(o.optString("type")).name,
                intensity = if (o.has("intensity") && !o.isNull("intensity")) {
                    o.getInt("intensity").takeIf { it in 1..10 }
                } else {
                    null
                },
                symptoms = o.optString("symptoms").toKeySet().filter { it in knownSymptoms }.joinToString(","),
                triggers = o.optString("triggers").toKeySet().filter { it in knownTriggers }.joinToString(","),
                medication = o.optString("medication").take(MAX_TEXT),
                medicationHelped = o.optString("medicationHelped").takeIf { key ->
                    MedHelp.entries.any { it.name == key }
                }.orEmpty(),
                notes = o.optString("notes").take(MAX_TEXT),
                temperature = o.optNumber("temperature"),
                pressure = o.optNumber("pressure"),
                pressureChange3h = o.optNumber("pressureChange3h"),
                pressureChange24h = o.optNumber("pressureChange24h"),
                humidity = o.optNumber("humidity")?.toInt(),
                weatherCode = o.optNumber("weatherCode")?.toInt(),
                sleepMinutes = o.optNumber("sleepMinutes")?.toInt(),
                steps24h = o.optNumber("steps24h")?.toInt(),
                restingHeartRate = o.optNumber("restingHeartRate")?.toInt(),
            )
        }

        // Копии старых версий не содержат этого поля — тогда отмеченных дней просто нет.
        val daysArray = root.optJSONArray("painFreeDays")
        val painFreeDays = if (daysArray == null) {
            emptyList()
        } else {
            (0 until daysArray.length()).map { daysArray.getLong(it) }
        }
        return BackupData(entries, painFreeDays)
    }
}
