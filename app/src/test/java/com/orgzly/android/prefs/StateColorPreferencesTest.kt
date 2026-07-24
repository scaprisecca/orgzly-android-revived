package com.orgzly.android.prefs

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class StateColorPreferencesTest {
    @Test
    fun stateColorPreferenceKeyUsesStableUtf8HexEncoding() {
        assertThat(
            StateColorPreferences.stateColorPreferenceKey("TODO"),
            `is`("pref_key_state_color_544f444f"),
        )
        assertThat(
            StateColorPreferences.stateColorPreferenceKey("WAITING"),
            `is`("pref_key_state_color_57414954494e47"),
        )
    }

    @Test
    fun stateColorPreferenceKeyKeepsPunctuationDistinct() {
        assertThat(
            StateColorPreferences.stateColorPreferenceKey("BLOCKED/EXT"),
            `is`("pref_key_state_color_424c4f434b45442f455854"),
        )
        assertThat(
            StateColorPreferences.stateColorPreferenceKey("BLOCKED EXT"),
            `is`("pref_key_state_color_424c4f434b454420455854"),
        )
    }

    @Test
    fun stateColorPreferenceKeySupportsUtf8StateNames() {
        assertThat(
            StateColorPreferences.stateColorPreferenceKey("À FAIRE"),
            `is`("pref_key_state_color_c380204641495245"),
        )
    }

    @Test
    fun detectsDynamicStateColorKeysByPrefix() {
        assertThat(
            StateColorPreferences.isStateColorPreferenceKey("pref_key_state_color_544f444f"),
            `is`(true),
        )
        assertThat(
            StateColorPreferences.isStateColorPreferenceKey("pref_key_states"),
            `is`(false),
        )
        assertThat(
            StateColorPreferences.isStateColorPreferenceKey(null),
            `is`(false),
        )
    }

    @Test
    fun parsesOnlyValidSixDigitHexColors() {
        assertThat(StateColorPreferences.parseColorHex("#00FF7F"), `is`(0xFF00FF7F.toInt()))
        assertThat(StateColorPreferences.parseColorHex("#00ff7f"), `is`(0xFF00FF7F.toInt()))
        assertThat(StateColorPreferences.parseColorHex("00FF7F"), `is`(nullValue()))
        assertThat(StateColorPreferences.parseColorHex("#12345"), `is`(nullValue()))
        assertThat(StateColorPreferences.parseColorHex("#GGGGGG"), `is`(nullValue()))
    }
}
