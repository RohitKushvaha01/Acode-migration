# Local plugin fixture

`install-plugin.zip` is a disposable plugin with ID `app.acode.ios-install-fixture`.
It contains its manifest, `main.js`, a short readme, Acode's existing generic plugin
icon, an empty directory and a five-byte binary file with a Unicode filename.
Inspect the readable entries with `unzip -p install-plugin.zip main.js` or
`unzip -p install-plugin.zip plugin.json`.

Its initializer reads the packaged binary asset, calls the legacy Cordova System
checksum API and reads a missing key from its isolated plugin context. It records
results in `window.iosInstalledPlugin`; unmount removes that marker. It does not
make purchases, authenticate, execute remote code or write secrets.

The integration test enters the local archive URL through the Plugins source
prompt, checks the Installed list and removes the plugin, cache and installation
state. It exercises the real installer and loader without exposing test hooks in
the app. A separate paid simulator UI check selected this fixture through Plugins
→ Local → Select document → the native Files picker and verified the same
installation results. Its temporary test and installed fixture were removed.
