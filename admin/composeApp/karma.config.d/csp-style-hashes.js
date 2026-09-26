// Serves the CSP style hash list to the browser tests under /csp-style-hashes.txt
const cspHashes = require('path').resolve(__dirname, '../../../../composeApp/src/wasmJsMain/resources/csp-style-hashes.txt');
config.files.push({ pattern: cspHashes, included: false, served: true, watched: false });
config.proxies = Object.assign({}, config.proxies, { '/csp-style-hashes.txt': '/absolute' + cspHashes });
