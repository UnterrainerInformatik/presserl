@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.installFocusGuard
import org.w3c.dom.Element
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test

/** A container like Compose web's: a shadow root holding a focusable canvas and a focused hidden input. */
private fun composeLikeContainer(): Element = js(
    """(() => {
    const container = document.createElement('div');
    document.body.appendChild(container);
    const host = document.createElement('div');
    container.appendChild(host);
    const root = host.attachShadow({ mode: 'open' });
    const canvas = document.createElement('canvas');
    canvas.setAttribute('tabindex', '0');
    root.appendChild(canvas);
    const input = document.createElement('input');
    root.appendChild(input);
    input.focus();
    return container;
})()""",
)

/** Removes the focused input, as Compose does when focus moves from a text field to a button. */
private fun removeInputAndCheckCanvasFocused(container: Element): Promise<JsAny?> = js(
    """new Promise((resolve, reject) => {
    const root = container.firstChild.shadowRoot;
    if (root.activeElement?.tagName !== 'INPUT') { reject(new Error('input not focused at start')); return; }
    root.querySelector('input').remove();
    setTimeout(() => {
        const focused = root.activeElement?.tagName;
        container.remove();
        focused === 'CANVAS' ? resolve(null) : reject(new Error('focus is on ' + document.activeElement.tagName));
    }, 20);
})""",
)

/** Moves focus to an input outside the container; the guard must leave it there. */
private fun focusOutsideAndCheckItStays(container: Element): Promise<JsAny?> = js(
    """new Promise((resolve, reject) => {
    const outside = document.createElement('input');
    document.body.appendChild(outside);
    outside.focus();
    setTimeout(() => {
        const stayed = document.activeElement === outside;
        outside.remove();
        container.remove();
        stayed ? resolve(null) : reject(new Error('focus moved to ' + document.activeElement.tagName));
    }, 20);
})""",
)

class FocusGuardTest {

    @Test
    fun focusFallingToTheBodyGoesBackToTheCanvas(): Promise<JsAny?> {
        val container = composeLikeContainer()
        installFocusGuard(container)
        return removeInputAndCheckCanvasFocused(container)
    }

    @Test
    fun focusOnAnotherElementIsLeftAlone(): Promise<JsAny?> {
        val container = composeLikeContainer()
        installFocusGuard(container)
        return focusOutsideAndCheckItStays(container)
    }
}
