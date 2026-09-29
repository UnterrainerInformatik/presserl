// Serves the start-up guard to the browser tests under /admin-startup.js
const startupJs = require('path').resolve(__dirname, '../../../../composeApp/src/wasmJsMain/resources/startup.js');
config.files.push({ pattern: startupJs, included: false, served: true, watched: false });
config.proxies = Object.assign({}, config.proxies, { '/admin-startup.js': '/absolute' + startupJs });
