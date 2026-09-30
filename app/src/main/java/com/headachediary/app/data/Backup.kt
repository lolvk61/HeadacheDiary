package com.headachediary.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Резервная копия в формате JSON: переносится между телефонами и читается человеком. */
object Backup {
    private const val APP_ID = "HeadacheDiary"
    private const val FORMAT_VERSION = 1
    private const val MAX_TEXT = 5_000

    fun toJson(entries: List<HeadacheEntry>, exportedAt: Long = System.currentTimeMillis()): String {
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
                },
            )
        }
        return JSONObject()
            .put("app", APP_ID)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", exportedAt)
            .put("entries", array)
            .toString(2)
    }

    /** Разбирает файл резервной копии. Бросает исключение, если файл не от этого приложения. */
    fun parse(text: String): List<HeadacheEntry> {
        val root = JSONObject(text)
        require(root.optString("app") == APP_ID) { "Not a Headache Diary backup" }
        val array = root.getJSONArray("entries")
        val knownSymptoms = Symptom.entries.map { it.name }.toSet()
        val knownTriggers = Trigger.entries.map { it.name }.toSet()

        return (0 until array.length()).map { i ->
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
            )
        }
    }
}
