package dev.kuclab.clock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val days: List<Int> = emptyList(),
    val label: String = "",
    val snooze: Boolean = true,
    val math: Boolean = true,
    val snoozeMinutes: Int = 5,
    val mathCount: Int = 3,
    // null = system default alarm ringtone. Otherwise either a content:// URI picked via
    // RingtoneManager, or one of BuiltInTones' android.resource:// URIs.
    val ringtoneUri: String? = null,
    // Extra "prove you're actually up" gates, independent of and stackable with math - all
    // enabled ones must be cleared before the alarm can be switched off, same as math.
    val stepsRequired: Boolean = false,
    val stepsCount: Int = 20,
    val shakeRequired: Boolean = false,
    val shakeCount: Int = 15,
    // Volume is intentionally stored per alarm. AlarmService applies it immediately before
    // playback and restores the previous system alarm volume when ringing stops.
    val volumePercent: Int = 80,
    val fadeInSeconds: Int = 20,
    // The ringing surface can have a different restrained atmosphere for each alarm.
    val wakeScene: String = WakeScene.AURORA.id,
    val wakeMessage: String = "",
    // Optional second line of defence: ask for confirmation after the alarm was dismissed,
    // then ring again if the prompt is ignored.
    val wakeCheckEnabled: Boolean = false,
    val wakeCheckDelayMinutes: Int = 5,
    val wakeCheckTimeoutMinutes: Int = 2,
    // Exact occurrence that should be ignored. Keeping the timestamp rather than a boolean
    // means edits/reboots cannot accidentally skip the wrong weekday.
    val skippedOccurrenceAt: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hour", hour)
        put("minute", minute)
        put("enabled", enabled)
        put("label", label)
        put("snooze", snooze)
        put("math", math)
        put("snoozeMinutes", snoozeMinutes)
        put("mathCount", mathCount)
        put("ringtoneUri", ringtoneUri)
        put("stepsRequired", stepsRequired)
        put("stepsCount", stepsCount)
        put("shakeRequired", shakeRequired)
        put("shakeCount", shakeCount)
        put("volumePercent", volumePercent)
        put("fadeInSeconds", fadeInSeconds)
        put("wakeScene", wakeScene)
        put("wakeMessage", wakeMessage)
        put("wakeCheckEnabled", wakeCheckEnabled)
        put("wakeCheckDelayMinutes", wakeCheckDelayMinutes)
        put("wakeCheckTimeoutMinutes", wakeCheckTimeoutMinutes)
        put("skippedOccurrenceAt", skippedOccurrenceAt)
        put("days", JSONArray().apply { days.forEach { put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val arr = o.optJSONArray("days")
            val days = if (arr != null) {
                (0 until arr.length()).map { arr.getInt(it) }
            } else emptyList()
            return Alarm(
                id = o.getLong("id"),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
                enabled = o.optBoolean("enabled", true),
                days = days,
                label = o.optString("label"),
                snooze = o.optBoolean("snooze", true),
                math = o.optBoolean("math", true),
                snoozeMinutes = o.optInt("snoozeMinutes", 5),
                mathCount = o.optInt("mathCount", 3),
                ringtoneUri = if (!o.has("ringtoneUri") || o.isNull("ringtoneUri")) null else o.getString("ringtoneUri"),
                stepsRequired = o.optBoolean("stepsRequired", false),
                stepsCount = o.optInt("stepsCount", 20),
                shakeRequired = o.optBoolean("shakeRequired", false),
                shakeCount = o.optInt("shakeCount", 15),
                volumePercent = o.optInt("volumePercent", 80).coerceIn(10, 100),
                fadeInSeconds = o.optInt("fadeInSeconds", 20).coerceIn(0, 60),
                wakeScene = WakeScene.fromId(o.optString("wakeScene")).id,
                wakeMessage = o.optString("wakeMessage", ""),
                wakeCheckEnabled = o.optBoolean("wakeCheckEnabled", false),
                wakeCheckDelayMinutes = o.optInt("wakeCheckDelayMinutes", 5).coerceIn(1, 15),
                wakeCheckTimeoutMinutes = o.optInt("wakeCheckTimeoutMinutes", 2).coerceIn(1, 5),
                skippedOccurrenceAt = if (!o.has("skippedOccurrenceAt") || o.isNull("skippedOccurrenceAt")) {
                    null
                } else {
                    o.optLong("skippedOccurrenceAt").takeIf { it > 0L }
                }
            )
        }

        private val dayNames = mapOf(
            1 to "po", 2 to "út", 3 to "st", 4 to "čt",
            5 to "pá", 6 to "so", 7 to "ne"
        )

        fun daySummary(days: List<Int>): String {
            if (days.isEmpty()) return "Jednorázově"
            if (days.size == 7) return "Každý den"
            if (days == listOf(6, 7)) return "Víkend"
            if (days == listOf(1, 2, 3, 4, 5)) return "Pracovní dny"
            return days.sorted().joinToString(", ") { dayNames[it] ?: "" }
        }
    }
}

enum class WakeScene(val id: String, val label: String, val description: String) {
    AURORA("aurora", "Polární záře", "Chladná, klidná a čistá"),
    DAWN("dawn", "První světlo", "Teplý úsvit bez ostrého jasu"),
    DEEP("deep", "Hluboká noc", "Minimální modrá pro citlivé oči"),
    EMBER("ember", "Žhavé ráno", "Energický jantarový akcent");

    companion object {
        fun fromId(id: String?): WakeScene = entries.firstOrNull { it.id == id } ?: AURORA
    }
}

object Alarms {
    private const val PREFS = "alarms_prefs"
    private const val KEY = "alarms"

    fun load(context: Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(context: Context, alarm: Alarm): List<Alarm> {
        val list = load(context).toMutableList()
        val i = list.indexOfFirst { it.id == alarm.id }
        if (i >= 0) list[i] = alarm else list.add(alarm)
        save(context, list)
        return list
    }

    fun remove(context: Context, id: Long): List<Alarm> {
        val list = load(context).filterNot { it.id == id }
        save(context, list)
        return list
    }

    fun setEnabled(context: Context, id: Long, enabled: Boolean): List<Alarm> {
        val list = load(context).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        save(context, list)
        return list
    }
}
