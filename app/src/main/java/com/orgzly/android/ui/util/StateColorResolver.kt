package com.orgzly.android.ui.util

import android.content.Context
import androidx.annotation.ColorInt
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.prefs.StateColorPreferences
import com.orgzly.android.ui.NoteStates

class StateColorResolver(
    private val context: Context,
    @ColorInt private val todoFallback: Int,
    @ColorInt private val doneFallback: Int,
) {
    @ColorInt
    fun colorForState(state: String?): Int {
        if (state.isNullOrBlank() || state == NoteStates.NO_STATE_KEYWORD) {
            return todoFallback
        }

        StateColorPreferences.parseColorHex(StateColorPreferences.stateColorHex(context, state))?.let {
            return it
        }

        return if (AppPreferences.isDoneKeyword(context, state)) {
            doneFallback
        } else {
            todoFallback
        }
    }
}
