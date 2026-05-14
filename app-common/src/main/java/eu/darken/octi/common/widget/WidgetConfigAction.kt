package eu.darken.octi.common.widget

/**
 * Footer-action model for [WidgetConfigScreen]. Lets the activity-side caller decide what the
 * sticky bottom CTA does without leaking Pro/upgrade concerns into the shared screen.
 */
sealed interface WidgetConfigAction {
    /** Show the Apply CTA. Disabled internally when the theme picker is in an invalid state. */
    data object Apply : WidgetConfigAction

    /** Show the Upgrade-to-Pro CTA. Always reachable — never gated by theme validity. */
    data object Upgrade : WidgetConfigAction

    /** Show an indeterminate progress indicator while Pro state is being resolved. */
    data object Loading : WidgetConfigAction

    /** Show an error message + retry CTA. */
    data class ErrorRetry(val message: String) : WidgetConfigAction
}
