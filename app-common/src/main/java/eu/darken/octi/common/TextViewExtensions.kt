package eu.darken.octi.common

import android.graphics.Typeface
import android.widget.TextView

var TextView.isBold: Boolean
    get() = typeface.isBold
    set(value) {
        setTypeface(null, if (value) Typeface.BOLD else Typeface.NORMAL)
    }