package eu.darken.octi.common.error

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.octi.common.R as CommonR

fun Throwable.asErrorDialogBuilder(
    activity: Activity
): MaterialAlertDialogBuilder {
    val context = activity
    return MaterialAlertDialogBuilder(context).apply {
        val error = this@asErrorDialogBuilder
        val localizedError = error.localized(context)

        setTitle(localizedError.label.get(context))
        setMessage(localizedError.description.get(context))

        if (localizedError.fixAction != null) {
            setPositiveButton(
                localizedError.fixActionLabel?.get(context) ?: context.getString(android.R.string.ok)
            ) { _, _ ->
                localizedError.fixAction!!.invoke(activity)
            }
            setNegativeButton(CommonR.string.general_cancel_action) { _, _ ->
            }
        } else {
            setPositiveButton(android.R.string.ok) { _, _ ->
            }
        }

        when {
            localizedError.infoAction != null -> {
                setNeutralButton(CommonR.string.general_show_details_action) { _, _ ->
                    localizedError.infoAction!!.invoke(activity)
                }
            }
        }
    }
}