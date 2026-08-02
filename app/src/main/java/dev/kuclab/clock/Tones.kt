package dev.kuclab.clock

import android.content.Context
import android.net.Uri

data class BuiltInTone(val id: String, val label: String, val resId: Int)

object BuiltInTones {
    val all = listOf(
        BuiltInTone("gentle", "Jemné", R.raw.tone_gentle),
        BuiltInTone("classic", "Klasické", R.raw.tone_classic),
        BuiltInTone("rising", "Vzestupné", R.raw.tone_rising)
    )

    fun uriFor(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun byUri(context: Context, uri: String?): BuiltInTone? {
        if (uri == null) return null
        return all.firstOrNull { uriFor(context, it.resId).toString() == uri }
    }
}
