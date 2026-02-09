package eu.darken.octi.common.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import eu.darken.octi.common.R

class SliderInputPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

    private var minValue: Int = 15
    private var maxValue: Int = 1440
    private var stepSize: Int = 15

    init {
        context.obtainStyledAttributes(attrs, R.styleable.SliderInputPreference).apply {
            minValue = getInteger(R.styleable.SliderInputPreference_minValue, 15)
            maxValue = getInteger(R.styleable.SliderInputPreference_maxValue, 1440)
            stepSize = getInteger(R.styleable.SliderInputPreference_stepSize, 15)
            recycle()
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val defVal = (defaultValue as? Int) ?: minValue
        val currentValue = getPersistedInt(defVal)
        updateSummary(currentValue)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
        return a.getInt(index, minValue)
    }

    override fun onClick() {
        val currentValue = getPersistedInt(minValue)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.preference_slider_input_dialog, null)

        val slider = dialogView.findViewById<Slider>(R.id.slider)
        val textInput = dialogView.findViewById<TextInputEditText>(R.id.text_input)

        slider.valueFrom = minValue.toFloat()
        slider.valueTo = maxValue.toFloat()
        slider.stepSize = stepSize.toFloat()
        slider.value = currentValue.toFloat().coerceIn(slider.valueFrom, slider.valueTo).let { value ->
            // Snap to nearest step for the slider
            val steps = ((value - slider.valueFrom) / slider.stepSize).toInt()
            (slider.valueFrom + steps * slider.stepSize).coerceIn(slider.valueFrom, slider.valueTo)
        }

        textInput.setText(currentValue.toString())

        var updatingFromSlider = false
        var updatingFromText = false

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !updatingFromText) {
                updatingFromSlider = true
                textInput.setText(value.toInt().toString())
                updatingFromSlider = false
            }
        }

        textInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (updatingFromSlider) return
                val typed = s?.toString()?.toIntOrNull() ?: return
                if (typed < minValue || typed > maxValue) return
                updatingFromText = true
                val snapped = typed.toFloat().let { value ->
                    val steps = ((value - slider.valueFrom) / slider.stepSize).toInt()
                    (slider.valueFrom + steps * slider.stepSize).coerceIn(slider.valueFrom, slider.valueTo)
                }
                slider.value = snapped
                updatingFromText = false
            }
        })

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = textInput.text?.toString()?.toIntOrNull()
                    ?.coerceIn(minValue, maxValue)
                    ?: currentValue
                if (callChangeListener(newValue)) {
                    persistInt(newValue)
                    updateSummary(newValue)
                }
            }
            .setNegativeButton(R.string.general_cancel_action, null)
            .show()
    }

    private fun updateSummary(value: Int) {
        summary = context.getString(R.string.preference_slider_input_summary, value)
    }
}
