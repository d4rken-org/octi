package eu.darken.octi.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import eu.darken.octi.R
import eu.darken.octi.common.getColorForAttr

class ThemedSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwipeRefreshLayout(context, attrs) {

    override fun onFinishInflate() {
        setProgressBackgroundColorSchemeColor(context.getColorForAttr(R.attr.colorPrimarySurface))
        setColorSchemeColors(
            context.getColorForAttr(R.attr.colorPrimary),
            context.getColorForAttr(R.attr.colorPrimaryDark)
        )
        super.onFinishInflate()
    }

}