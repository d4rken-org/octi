package eu.darken.octi.common.debug

import android.app.Application

interface AutomaticBugReporter {

    fun setup(application: Application)

    fun notify(throwable: Throwable)
}