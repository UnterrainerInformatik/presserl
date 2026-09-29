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
 * startup.js checks WebAssembly and WebGL before the app is loaded; a direct composeApp.js tag
 * in index.html would bypass it and bring back the blank page.
 */
class StartupScriptTest {

    @Test
    fun indexLoadsOnlyTheStartupScript(): Promise<JsAny?> = verifyIndexLoadsStartupScript()
}
