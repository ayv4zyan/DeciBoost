package com.deciboost.core.data

/**
 * User-selectable app color theme (Settings → Appearance).
 *
 * Fixed styles use an OLED-black chassis with distinct accents.
 * [DYNAMIC] / [DYNAMIC_OLED] follow Material You from the wallpaper (Android 12+).
 */
enum class ThemeStyle(val storageKey: String) {
    /** Material You — wallpaper accents + system dark surfaces (API 31+; falls back to [VIOLET]). */
    DYNAMIC("dynamic"),

    /**
     * Material You accents on a pure OLED-black chassis (API 31+; falls back to [VIOLET]).
     * Wallpaper primary/secondary/tertiary; surfaces forced true black.
     */
    DYNAMIC_OLED("dynamic_oled"),

    /** OLED black + violet primary (former default / issue #6 baseline accents). */
    VIOLET("violet"),

    /** OLED black + logo-faithful cyan. */
    CYAN("cyan"),

    /** OLED black + warm amber VU accents. */
    WARM("warm"),

    /** OLED black + phosphor green (scope / monochrome brand). */
    SCOPE("scope"),
    ;

    companion object {
        /**
         * Fresh install default: Material You accents on true-black surfaces.
         * Pre–Android 12 falls back to violet OLED when resolving the scheme.
         */
        val DEFAULT: ThemeStyle = DYNAMIC_OLED

        fun fromStorageKey(key: String?): ThemeStyle =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
