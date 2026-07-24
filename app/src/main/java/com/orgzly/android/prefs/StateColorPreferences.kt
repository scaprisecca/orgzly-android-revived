package com.orgzly.android.prefs

import android.content.Context
import androidx.annotation.ColorInt
import androidx.preference.PreferenceManager
import java.nio.charset.StandardCharsets
import java.util.Locale

object StateColorPreferences {
    private const val PREFERENCE_KEY_PREFIX = "pref_key_state_color_"

    @JvmStatic
    fun stateColorPreferenceKey(state: String): String {
        return PREFERENCE_KEY_PREFIX + encodeStateKeyword(state)
    }

    @JvmStatic
    fun isStateColorPreferenceKey(key: String?): Boolean {
        return key?.startsWith(PREFERENCE_KEY_PREFIX) == true
    }

    @JvmStatic
    fun stateColorHex(context: Context, state: String): String? {
        val value = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(stateColorPreferenceKey(state), null)

        return normalizeColorHex(value)
    }

    @JvmStatic
    @ColorInt
    fun stateColor(context: Context, state: String, @ColorInt fallbackColor: Int): Int {
        return parseColorHex(stateColorHex(context, state)) ?: fallbackColor
    }

    @JvmStatic
    fun defaultColorHex(@ColorInt color: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }

    @JvmStatic
    fun parseColorHex(hex: String?): Int? {
        val normalized = normalizeColorHex(hex) ?: return null
        return normalized.substring(1).toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
    }

    internal fun encodeStateKeyword(state: String): String {
        val bytes = state.toByteArray(StandardCharsets.UTF_8)
        val chars = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef".toCharArray()

        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }

        return String(chars)
    }

    private fun normalizeColorHex(hex: String?): String? {
        if (hex == null || !HEX_COLOR_PATTERN.matches(hex)) {
            return null
        }

        return hex.uppercase(Locale.US)
    }

    private val HEX_COLOR_PATTERN = Regex("^#[0-9a-fA-F]{6}$")
}
