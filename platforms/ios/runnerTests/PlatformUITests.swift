import XCTest
import WebKit
#if ACODE_FREE
@testable import runnerFree
#else
@testable import runner
#endif

@MainActor
final class PlatformUITests: BridgeTestCase {
    func testEmptyNavigationDoesNotOfferToExitButStillClosesDialogs() async throws {
        let webView = try await editorWebView()
        let result = try await webView.callAsyncJavaScript("""
            const stack = acode.require('actionStack');
            const settings = acode.require('settings');
            if(stack.length) throw Error('Navigation fixture needs an empty stack');
            const original = {confirm:settings.value.confirmOnExit, close:stack.onCloseApp, exec:Bridge.exec};
            let exits = 0, callbacks = 0;
            Bridge.exec = (...args) => {
                if(args[2] === 'App' && args[3] === 'exitApp') exits++;
                return original.exec(...args);
            };
            stack.onCloseApp = () => {callbacks++;};
            try {
                for(const confirmOnExit of [true, false]) {
                    settings.value.confirmOnExit = confirmOnExit;
                    const pending = stack.pop();
                    await new Promise(resolve => setTimeout(resolve,50));
                    const unexpected = document.querySelector('.prompt.confirm:not(.hide)');
                    if(unexpected) {
                        unexpected.querySelector('button').click();
                        await pending;
                        throw Error('iOS offered to exit');
                    }
                    await pending;
                }
                const confirmation = acode.require('confirm')('Navigation fixture', 'Dismiss this dialog');
                if(stack.length !== 1) throw Error('Dialog was not stacked');
                await stack.pop();
                if(await confirmation !== false || stack.length) throw Error('Dialog dismissal changed');
                if(exits || callbacks) throw Error('Unsupported exit flow invoked');
                return true;
            } finally {
                settings.value.confirmOnExit = original.confirm;
                stack.onCloseApp = original.close;
                Bridge.exec = original.exec;
            }
            """, arguments: [:], in: nil, contentWorld: .page) as? Bool
        XCTAssertEqual(result, true)
    }

    func testSettingsAboutAndDiagnosticsAvoidAndroidStoreActions() async throws {
        let webView = try await editorWebView()
        let result = try await webView.callAsyncJavaScript("""
            const wait=async check=>{for(let n=0;n<100;n++){if(check())return;await new Promise(r=>setTimeout(r,50));}throw Error('Platform UI did not settle');};
            const openBrowser=system.openInBrowser, copy=Bridge.clipboard.copy;
            const opened=[];
            let copied='';
            system.openInBrowser=(...args)=>opened.push(args);
            Bridge.clipboard.copy=value=>{copied=value;};
            const stack=acode.require('actionStack');
            let settingsPage;
            try {
                await acode.exec('open','settings');
                await wait(()=>document.querySelector('.main-settings-page [data-key="about"]'));
                settingsPage=acode.require('settings').uiSettings['main-settings'];
                const rating=document.querySelector('.main-settings-page [data-key="rateapp"]');
                if(rating&&!rating.hidden)throw Error('Google Play rating is visible on iOS');
                if(settingsPage.search('rate').some(item=>item.dataset.key==='rateapp'))throw Error('Search reveals unavailable rating action');
                const pro=document.querySelector('.main-settings-page [data-key="removeads"]');
                if(pro&&(pro.textContent.includes(strings['iap-pro-purchase-warning'])||!pro.textContent.includes('Apple Account')))throw Error('Pro restoration instructions target the wrong store');
                rating?.click();
                document.querySelector('.main-settings-page [data-key="about"]').click();
                await wait(()=>document.querySelector('#about-page')?.textContent.includes('com.apple.WebKit'));
                const info=document.querySelector('#about-page .info-item');
                if(info.hasAttribute('href')||!info.textContent.includes('WebKit'))throw Error('WebKit information offers an Android update link');
                info.click();
                if(opened.length)throw Error('About opened an external store');
                await acode.exec('copy-device-info');
                await wait(()=>copied.length>0);
                if(!copied.includes('iOS Version: '+device.version)||copied.includes('Android Version:'))throw Error('Incorrect platform in device report');
                if(!copied.includes('com.apple.WebKit'))throw Error('Device report lost native WebKit information');
                return true;
            } finally {
                system.openInBrowser=openBrowser;Bridge.clipboard.copy=copy;
                if(document.getElementById('about-page'))stack.pop();
                settingsPage?.hide();
            }
            """, arguments: [:], in: nil, contentWorld: .page) as? Bool
        XCTAssertEqual(result, true)
    }
}
