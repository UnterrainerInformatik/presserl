// Serves the localized string files to the browser tests under /strings/<qualifier>/strings.xml
const resources = require('path').resolve(__dirname, '../../../../composeApp/src/commonMain/composeResources');
['values', 'values-en'].forEach(qualifier => {
    const file = resources + '/' + qualifier + '/strings.xml';
    config.files.push({ pattern: file, included: false, served: true, watched: false });
    config.proxies = Object.assign({}, config.proxies, { ['/strings/' + qualifier + '/strings.xml']: '/absolute' + file });
});
