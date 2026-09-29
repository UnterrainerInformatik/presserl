@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test

/** Rejects unless the entry page loads only startup.js, which in turn loads composeApp.js. */
private fun verifyIndexLoadsStartupScript(): Promise<JsAny?> = js(
    """fetch('/admin-index.html').then(response => response.text()).then(html => {
    const sources = Array.from(new DOMParser().parseFromString(html, 'text/html').scripts, script => script.getAttribute('src'));
    if (sources.join(',') !== 'startup.js') throw new Error('index.html must load only startup.js, but loads: ' + sources.join(', '));
    return null;
})""",
)

/**
 * Runs startup.js in an iframe whose composeApp.js never arrives (a stand-in for a slow download)
 * and marks the app as started after [startAfterMillis]. Rejects unless the loading indicator was
 * present 700 ms after start-up exactly when [expectShown], and is gone once the app has started.
 */
private fun verifyLoadingIndicator(startAfterMillis: Int, expectShown: Boolean): Promise<JsAny?> = js(
    """new Promise((resolve, reject) => {
    const frame = document.createElement('iframe');
    frame.srcdoc = '<!DOCTYPE html><html><head></head><body></body></html>';
    frame.onload = () => {
        const win = frame.contentWindow;
        const doc = frame.contentDocument;
        const appendChild = win.HTMLHeadElement.prototype.appendChild;
        win.HTMLHeadElement.prototype.appendChild = function (node) {
            return node.src && node.src.endsWith('/composeApp.js') ? node : appendChild.call(this, node);
        };
        const loading = () => !!doc.getElementById('presserl-startup-loading');
        const script = doc.createElement('script');
        script.src = '/admin-startup.js';
        script.onerror = () => reject(new Error('startup.js could not be loaded'));
        script.onload = () => {
            let shownBeforeStart = null;
            win.setTimeout(() => { shownBeforeStart = loading(); }, 700);
            win.setTimeout(() => { doc.documentElement.dataset.presserlStarted = 'true'; }, startAfterMillis);
            win.setTimeout(() => {
                const result = JSON.stringify({ shownBeforeStart: shownBeforeStart, shownAfterStart: loading(), notice: !!doc.getElementById('presserl-startup-notice') });
                const expected = JSON.stringify({ shownBeforeStart: expectShown, shownAfterStart: false, notice: false });
                frame.remove();
                if (result === expected) resolve(null); else reject(new Error('expected ' + expected + ' but got ' + result));
            }, Math.max(startAfterMillis, 700) + 100);
        };
        appendChild.call(doc.head, script);
    };
    document.body.appendChild(frame);
})""",
)

/**
 * startup.js checks WebAssembly and WebGL before the app is loaded; a direct composeApp.js tag
 * in index.html would bypass it and bring back the blank page.
 */
class StartupScriptTest {

    @Test
    fun indexLoadsOnlyTheStartupScript(): Promise<JsAny?> = verifyIndexLoadsStartupScript()

    @Test
    fun slowLoadShowsIndicatorUntilFirstFrame(): Promise<JsAny?> =
        verifyLoadingIndicator(startAfterMillis = 1000, expectShown = true)

    @Test
    fun fastLoadNeverShowsIndicator(): Promise<JsAny?> =
        verifyLoadingIndicator(startAfterMillis = 200, expectShown = false)
}
