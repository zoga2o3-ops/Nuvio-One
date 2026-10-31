package com.nuvio.tv.youtube

import android.content.Context

/** Your YouTube choices, kept on this device. */
object YouTubeSettings {
    private const val PREFS = "youtube_settings"
    private const val KEY_SPONSORBLOCK = "sponsorblock_enabled"
    private const val KEY_CATEGORIES = "sponsorblock_categories"
    private const val KEY_QUALITY = "preferred_height"

    /** SponsorBlock's segment types, in the order they're listed. */
    val sponsorCategories: List<Pair<String, String>> = listOf(
        "sponsor" to "Sponsor",
        "selfpromo" to "Self-promotion",
        "interaction" to "Like and subscribe reminders",
        "intro" to "Intros",
        "outro" to "Outros and end cards",
        "preview" to "Previews and recaps",
        "music_offtopic" to "Non-music parts",
        "filler" to "Filler"
    )

    private val defaultCategories = setOf("sponsor", "selfpromo", "interaction")

    /** The sizes you can set as a default; 0 is automatic. */
    val qualityChoices = listOf(0, 2160, 1440, 1080, 720, 480, 360)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun sponsorBlockEnabled(context: Context) = prefs(context).getBoolean(KEY_SPONSORBLOCK, true)

    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPONSORBLOCK, enabled).apply()
    }

    fun sponsorCategoriesOn(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CATEGORIES, null)?.toSet() ?: defaultCategories

    fun setSponsorCategory(context: Context, category: String, on: Boolean) {
        val next = sponsorCategoriesOn(context).toMutableSet().apply { if (on) add(category) else remove(category) }
        prefs(context).edit().putStringSet(KEY_CATEGORIES, next).apply()
    }

    /** The video height to start at; 0 lets the player choose. */
    fun preferredHeight(context: Context) = prefs(context).getInt(KEY_QUALITY, 0)

    fun setPreferredHeight(context: Context, height: Int) {
        prefs(context).edit().putInt(KEY_QUALITY, height).apply()
    }

    fun qualityLabel(height: Int) = if (height <= 0) "Auto" else "${height}p"
}
