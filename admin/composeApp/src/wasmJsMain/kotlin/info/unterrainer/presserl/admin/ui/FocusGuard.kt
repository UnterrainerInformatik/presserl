@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin.ui

import org.w3c.dom.Element
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

/**
 * Keeps keys reaching Compose. Compose web edits text through a hidden input in its shadow root; tabbing from a text
 * field to a button removes that input while it holds browser focus, the browser focuses the page body and no key
 * reaches the app any more. After every focus loss the guard gives focus back to the app's canvas (found in a shadow
 * root below [container]) when it landed on the body; focus that went to another element is left alone.
 */
fun installFocusGuard(container: Element): Unit = js(
    """{
    const doc = container.ownerDocument;
    doc.addEventListener('focusout', () => setTimeout(() => {
        if (doc.activeElement !== doc.body) return;
        const host = [...container.querySelectorAll('*')].find(element => element.shadowRoot?.querySelector('canvas'));
        host?.shadowRoot.querySelector('canvas').focus({ preventScroll: true });
    }, 0), true);
}""",
)
