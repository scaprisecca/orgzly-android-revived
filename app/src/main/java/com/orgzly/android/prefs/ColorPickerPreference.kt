package com.orgzly.android.prefs

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.PreferenceViewHolder
import com.orgzly.R
import java.util.Locale

class ColorPickerPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs) {

    init {
        dialogLayoutResource = R.layout.dialog_color_picker
        widgetLayoutResource = R.layout.preference_color_swatch
    }

    private var color: Int = Color.RED
    private var colorHex: String = "#FF0000"
    private var defaultColorHex: String = colorHex

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference)
        try {
            val defaultValue = a.getString(R.styleable.ColorPickerPreference_android_defaultValue)
            if (defaultValue != null) {
                setDefaultColorHex(defaultValue)
            }
        } finally {
            a.recycle()
        }

        updateSummary()
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val initialValue = getPersistedString(defaultValue as? String ?: defaultColorHex)
        colorHex = initialValue
        color = parseColor(colorHex)
        updateSummary()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    fun setColor(newColor: Int) {
        color = newColor
        colorHex = String.format(Locale.US, "#%06X", 0xFFFFFF and newColor)
        if (callChangeListener(colorHex)) {
            persistString(colorHex)
            updateSummary()
            notifyChanged()
        }
    }

    fun getColor(): Int = color

    fun setDefaultColorHexForDynamicPreference(hex: String) {
        setDefaultColorHex(hex)
        colorHex = defaultColorHex
        color = parseColor(colorHex)
        updateSummary()
        notifyChanged()
    }

    private fun parseColor(hex: String): Int {
        return StateColorPreferences.parseColorHex(hex) ?: Color.RED
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val swatch = holder.findViewById(R.id.color_swatch)
        swatch?.setBackgroundColor(color)
    }

    private fun updateSummary() {
        summary = colorHex
    }

    private fun setDefaultColorHex(hex: String) {
        defaultColorHex = StateColorPreferences.defaultColorHex(
            StateColorPreferences.parseColorHex(hex) ?: Color.RED
        )
        colorHex = defaultColorHex
        color = parseColor(defaultColorHex)
    }

}
