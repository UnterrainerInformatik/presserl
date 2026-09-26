// Compose renders through WebGL; headless Chrome without a GPU needs the software renderer
config.customLaunchers = Object.assign({}, config.customLaunchers, {
    ChromeHeadlessSoftwareGL: { base: 'ChromeHeadless', flags: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
});
config.browsers = ['ChromeHeadlessSoftwareGL'];
