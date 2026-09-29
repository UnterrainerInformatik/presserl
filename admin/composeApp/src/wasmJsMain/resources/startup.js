// Start-up guard for the admin app. It checks the prerequisites (WebAssembly, WebGL) before the
// Wasm bundle is downloaded, loads composeApp.js only when they are met and otherwise shows a
// plain HTML notice instead of a blank page. Until the app has drawn its first frame (Main.kt sets
// data-presserl-started), load failures and uncaught errors show a generic "could not start"
// notice. No innerHTML and no inline styles: the admin CSP stays as strict as it is.
(function () {
    "use strict";

    var TEXTS = {
        de: {
            webglTitle: "Die Verwaltung braucht WebGL",
            webglText: "Dein Browser erlaubt dieser Seite gerade kein WebGL. Die Verwaltung zeichnet ihre ganze Oberfläche damit und kann ohne WebGL nicht starten. So erlaubst du es und lädst die Seite danach neu:",
            webglRemedies: [
                "Wenn in der Adressleiste ein Hinweis zu WebGL oder Canvas erscheint: für diese Seite erlauben.",
                "Firefox: about:config öffnen und webgl.disabled auf false setzen.",
                "Fingerprinting- oder Canvas-Blocker (z. B. CanvasBlocker, resistFingerprinting): eine Ausnahme für diese Seite eintragen.",
            ],
            wasmTitle: "Dieser Browser wird nicht unterstützt",
            wasmText: "Die Verwaltung braucht WebAssembly, und das kann dieser Browser nicht. Bitte verwende eine aktuelle Version von Firefox, Chrome, Edge oder Safari.",
            failedTitle: "Die Verwaltung konnte nicht starten",
            failedText: "Beim Laden ist ein Fehler aufgetreten. Bitte lade die Seite neu. Wenn das nicht hilft, versuche es später noch einmal oder mit einem anderen Browser.",
        },
        en: {
            webglTitle: "The administration needs WebGL",
            webglText: "Your browser does not allow WebGL for this site right now. The administration draws its whole interface with it and cannot start without WebGL. To allow it, then reload the page:",
            webglRemedies: [
                "If the address bar shows a notice about WebGL or canvas: allow it for this site.",
                "Firefox: open about:config and set webgl.disabled to false.",
                "Fingerprinting or canvas blockers (e.g. CanvasBlocker, resistFingerprinting): add an exception for this site.",
            ],
            wasmTitle: "This browser is not supported",
            wasmText: "The administration needs WebAssembly, which this browser does not support. Please use a current version of Firefox, Chrome, Edge or Safari.",
            failedTitle: "The administration could not start",
            failedText: "An error occurred while loading. Please reload the page. If that does not help, try again later or with another browser.",
        },
    };

    var language = String(navigator.language || "").toLowerCase().indexOf("en") === 0 ? "en" : "de";
    var texts = TEXTS[language];

    function hasWebAssembly() {
        return typeof WebAssembly === "object" && typeof WebAssembly.instantiate === "function";
    }

    // Mirrors the context Skiko requests; null or an exception both mean "no WebGL".
    function hasWebGl() {
        try {
            var canvas = document.createElement("canvas");
            return !!(canvas.getContext("webgl2") || canvas.getContext("webgl"));
        } catch (e) {
            return false;
        }
    }

    function started() {
        return document.documentElement.dataset.presserlStarted === "true";
    }

    function showNotice(title, text, remedies) {
        if (document.getElementById("presserl-startup-notice")) return;
        var notice = document.createElement("div");
        notice.id = "presserl-startup-notice";
        notice.setAttribute("role", "alert");
        notice.lang = language;
        var heading = document.createElement("h1");
        heading.textContent = title;
        notice.appendChild(heading);
        var paragraph = document.createElement("p");
        paragraph.textContent = text;
        notice.appendChild(paragraph);
        if (remedies) {
            var list = document.createElement("ul");
            remedies.forEach(function (remedy) {
                var item = document.createElement("li");
                item.textContent = remedy;
                list.appendChild(item);
            });
            notice.appendChild(list);
        }
        document.body.appendChild(notice);
    }

    function showWhenReady(title, text, remedies) {
        if (document.body) {
            showNotice(title, text, remedies);
        } else {
            document.addEventListener("DOMContentLoaded", function () {
                showNotice(title, text, remedies);
            });
        }
    }

    function showStartFailure() {
        if (!started()) showWhenReady(texts.failedTitle, texts.failedText);
    }

    // Errors without a file name come from Wasm/the runtime; extension scripts have their own scheme.
    function isOwnError(event) {
        var file = event.filename;
        return !file || file.indexOf(location.origin + "/") === 0;
    }

    if (!hasWebAssembly()) {
        showWhenReady(texts.wasmTitle, texts.wasmText);
        return;
    }
    if (!hasWebGl()) {
        showWhenReady(texts.webglTitle, texts.webglText, texts.webglRemedies);
        return;
    }

    window.addEventListener("error", function (event) {
        if (isOwnError(event)) showStartFailure();
    });
    window.addEventListener("unhandledrejection", showStartFailure);

    var script = document.createElement("script");
    script.src = "composeApp.js";
    script.onerror = showStartFailure;
    document.head.appendChild(script);
})();
