/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser

import android.app.Application
import android.os.Handler
import android.os.Looper
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.rustlog.RustLog
import org.mozilla.reference.browser.ext.isCrashReportActive



open class BrowserApplication : Application() {
    val components by lazy { Components(this) }
   private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate() {
        super.onCreate()

        initializePreMainComponents()
        if (!isMainProcess()) {
            // If this is not the main process then do not continue with the initialization here. Everything that
            // follows only needs to be done in our app's main process and should not be done in other processes like
            // a GeckoView child process or the crash handling process. Most importantly we never want to end up in a
            // situation where we create a GeckoRuntime from the Gecko child process (
            return
        }

        initializePostMainComponents()

    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            components.core.store.dispatch(SystemAction.LowMemoryAction(level))
            components.core.icons.onTrimMemory(level)
        }
    }

    companion object {
        const val NON_FATAL_CRASH_BROADCAST = "org.mozilla.reference.browser"
    }

    private fun initializePreMainComponents() {
        mainHandler.post {
            initializeCrashReporting()
            setUpRustHttpConfig()
            initializeLogging()
        }
    }

    private fun initializeCrashReporting() {
        setupCrashReporting(this@BrowserApplication)
    }

    private fun setUpRustHttpConfig() {
        RustHttpConfig.setClient(lazy { components.core.client })
    }

    private fun initializeLogging() {
        setupLogging()
    }


    private fun initializePostMainComponents() {
        mainHandler.post {
            initializeGlobalAddonDependency()
            initializeAnalytics()
        }
    }


    private fun initializeGlobalAddonDependency() {
        GlobalAddonDependencyProvider.initialize(
                components.core.addonManager,
                components.core.addonUpdater
        )
    }

    private fun initializeAnalytics() {
        components.analytics.initializeGlean()
    }

    private fun setupLogging() {
        // We want the log messages of all builds to go to Android logcat
        Log.addSink(AndroidLogSink())
        RustLog.enable()
    }

    private fun setupCrashReporting(application: BrowserApplication) {
        if (isCrashReportActive) {
            application
                    .components
                    .analytics
                    .crashReporter.install(application)
        }
    }

}

