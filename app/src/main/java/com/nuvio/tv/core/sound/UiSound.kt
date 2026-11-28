package com.nuvio.tv.core.sound

import com.nuvio.tv.R

/**
 * The navigation sounds offered in Settings → Layout.
 *
 * All of them are short (35-110 ms) and deliberately unobtrusive: the long, musical ones from the
 * first round were dropped after testing, because a sound with a tail smears into a buzz when the
 * highlight is moved quickly. [id] is what is stored in preferences, so these strings must not
 * change once a build has shipped.
 */
enum class UiSound(val id: String, val label: String, val description: String, val resId: Int) {
    SOFT_TICK(
        id = "soft_tick",
        label = "Soft tick",
        description = "Light and airy. The quietest of the ticks.",
        resId = R.raw.sfx_soft_tick,
    ),
    WOODEN_TOK(
        id = "wooden_tok",
        label = "Wooden tok",
        description = "Warm, wooden knock. Audible at low volume.",
        resId = R.raw.sfx_wooden_tok,
    ),
    CRISP_CLICK(
        id = "crisp_click",
        label = "Crisp click",
        description = "Short and mechanical, like a physical button.",
        resId = R.raw.sfx_crisp_click,
    ),
    APPLE_TOCK(
        id = "apple_tock",
        label = "Soft tock",
        description = "A low, soft tock, in the manner of Apple TV. Felt more than heard.",
        resId = R.raw.sfx_apple_tock,
    ),
    FIRE_TICK(
        id = "fire_tick",
        label = "Bright tick",
        description = "Brighter and more mechanical, in the manner of Fire TV.",
        resId = R.raw.sfx_fire_tick,
    ),
    SYSTEM_TICK(
        id = "system_tick",
        label = "System tick",
        description = "Dry and neutral, close to Android TV's own navigation tick.",
        resId = R.raw.sfx_system_tick,
    ),
    ROKU_BLOOP(
        id = "roku_bloop",
        label = "Rounded bloop",
        description = "Rounded and slightly lower, in the manner of Roku.",
        resId = R.raw.sfx_roku_bloop,
    ),
    MUTED_CLICK(
        id = "muted_click",
        label = "Muted click",
        description = "Heavily dulled and unobtrusive.",
        resId = R.raw.sfx_muted_click,
    ),
    FELT_TICK(
        id = "felt_tick",
        label = "Felt tick",
        description = "The quietest option, little more than a texture.",
        resId = R.raw.sfx_felt_tick,
    );

    companion object {
        /** Wooden tok: soft, but clearly audible on TV speakers. */
        val DEFAULT = WOODEN_TOK

        fun fromId(id: String?): UiSound = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
