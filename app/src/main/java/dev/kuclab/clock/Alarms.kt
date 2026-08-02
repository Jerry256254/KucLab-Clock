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
    val ringtoneUri: String? = null
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
                ringtoneUri = if (!o.has("ringtoneUri") || o.isNull("ringtoneUri")) null else o.getString("ringtoneUri")
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
