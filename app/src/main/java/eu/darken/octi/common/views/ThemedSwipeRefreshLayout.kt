package eu.darken.octi.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.R as MaterialR
import eu.darken.octi.common.getColorForAttr

class ThemedSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    override fun onFinishInflate() {
        setProgressBackgroundColorSchemeColor(context.getColorForAttr(MaterialR.attr.colorSurface))
        setColorSchemeColors(
            context.getColorForAttr(MaterialR.attr.colorPrimary),
            context.getColorForAttr(MaterialR.attr.colorPrimaryDark)
        )
        super.onFinishInflate()
    }

}