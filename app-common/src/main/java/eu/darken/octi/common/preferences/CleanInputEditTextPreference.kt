package eu.darken.octi.common.preferences

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.preference.EditTextPreference

class CleanInputEditTextPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle
) : EditTextPreference(context, attrs, defStyleAttr) {

    init {
        setOnBindEditTextListener { editText ->
            editText.apply {
                isSingleLine = true
                maxLines = 1
                imeOptions = EditorInfo.IME_ACTION_DONE
                filters = arrayOf(
                    InputFilter { source, start, end, dest, dstart, dend ->
                        for (i in start until end) {
                            if (source[i] == '\n' || source[i] == '\r') {
                                return@InputFilter ""
                            }
                        }
                        null
                    }
                )
            }
        }
    }

    override fun persistString(value: String?): Boolean {
        val trimmedValue = value?.trim()
        return super.persistString(trimmedValue)
    }
}