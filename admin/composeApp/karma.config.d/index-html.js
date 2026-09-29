// Serves the admin entry page to the browser tests under /admin-index.html
const indexHtml = require('path').resolve(__dirname, '../../../../composeApp/src/wasmJsMain/resources/index.html');
config.files.push({ pattern: indexHtml, included: false, served: true, watched: false });
config.proxies = Object.assign({}, config.proxies, { '/admin-index.html': '/absolute' + indexHtml });
