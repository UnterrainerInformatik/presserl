@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalComposeUiApi::class)

package info.unterrainer.presserl.admin

import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test

private fun recordStyleElements(): Unit = js(
    """{
    globalThis.presserlStyles = [];
    const append = Node.prototype.appendChild;
    Node.prototype.appendChild = function (child) {
        if (child && child.nodeName === 'STYLE') globalThis.presserlStyles.push(child.textContent);
        return append.call(this, child);
    };
}""",
)

/**
 * Waits (real time) until Compose has attached its style, hashes every recorded style as a CSP
 * source expression and rejects unless csp-style-hashes.txt lists all of them.
 */
private fun verifyRecordedStylesAreListed(timeoutMillis: Int): Promise<JsAny?> = js(
    """new Promise(resolve => {
    const started = Date.now();
    const poll = () => globalThis.presserlStyles.length > 0 || Date.now() - started > timeoutMillis
        ? resolve() : setTimeout(poll, 50);
    poll();
}).then(() => {
    if (globalThis.presserlStyles.length === 0) {
        throw new Error('Compose injected no style element; is the recording hook still effective?');
    }
    return Promise.all([
        Promise.all(globalThis.presserlStyles.map(text =>
            crypto.subtle.digest('SHA-256', new TextEncoder().encode(text)).then(digest =>
                "'sha256-" + btoa(String.fromCharCode(...new Uint8Array(digest))) + "'"))),
        fetch('/csp-style-hashes.txt').then(response => response.text()),
    ]);
}).then(([injected, file]) => {
    const listed = file.split('\n').map(line => line.trim()).filter(line => line && !line.startsWith('#'));
    const missing = injected.filter(hash => !listed.includes(hash));
    if (missing.length > 0) throw new Error('Add to csp-style-hashes.txt: ' + missing.join(' '));
    return null;
})""",
)

/**
 * The admin CSP allows Compose's injected style by hash (csp-style-hashes.txt, read by the
 * backend); a Compose upgrade that changes the style must fail here, not in the browser.
 */
class CspStyleHashTest {

    @Test
    fun injectedComposeStylesAreListed(): Promise<JsAny?> {
        recordStyleElements()
        val host = document.createElement("div")
        document.body!!.appendChild(host)
        ComposeViewport(host) { Text("CSP") }
        return verifyRecordedStylesAreListed(10_000)
    }
}
