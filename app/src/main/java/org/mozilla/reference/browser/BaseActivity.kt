package org.mozilla.reference.browser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.session.Session
import mozilla.components.concept.push.PushProcessor
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.reference.browser.push.PushFxaIntegration
import org.mozilla.reference.browser.push.WebPushEngineIntegration
import java.util.concurrent.TimeUnit

abstract class BaseActivity  : AppCompatActivity() {
   private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    val components by lazy { (application as BrowserApplication).components }
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        mainHandler.post {
            restoreBrowser()
            initializeWebExtensionSupport()
            initialisePushProcessor()
        }
    }

    private fun initializeWebExtensionSupport() {
        WebExtensionSupport.initialize(
                runtime = components.core.engine,
                store = components.core.store,
                onNewTabOverride = { _, engineSession, url ->
                    val session = Session(url)
                    components.core.sessionManager.add(session, true, engineSession)
                    session.id
                },
                onCloseTabOverride = { _, sessionId ->
                    components.useCases.tabsUseCases.removeTab(sessionId)
                },
                onSelectTabOverride = { _, sessionId ->
                    val selected = components.core.sessionManager.findSessionById(sessionId)
                    selected?.let { components.useCases.tabsUseCases.selectTab(it.id) }
                },
                onUpdatePermissionRequest = components.core.addonUpdater::onUpdatePermissionRequest
        )

    }
    private fun initialisePushProcessor() {
        components.push.feature?.let {
            Logger.info("AutoPushFeature is configured, initializing it...")

            PushProcessor.install(it)

            // WebPush integration to observe and deliver push messages to engine.
            WebPushEngineIntegration(components.core.engine, it).start()

            // Perform a one-time initialization of the account manager if a message is received.
            PushFxaIntegration(it, lazy { components.backgroundServices.accountManager }).launch()

            // Initialize the push feature and service.
            it.initialize()
        }
    }

    private fun restoreBrowser() {
        components.core.engine.warmUp()
        restoreBrowserState()
    }
    private fun restoreBrowserState() = GlobalScope.launch(Dispatchers.Main) {
        val store = components.core.store
        val sessionStorage = components.core.sessionStorage

        components.useCases.tabsUseCases.restore(sessionStorage)

        // Now that we have restored our previous state (if there's one) let's setup auto saving the state while
        // the app is used.
        sessionStorage.autoSave(store)
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
    }
}