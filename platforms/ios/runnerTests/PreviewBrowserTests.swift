import XCTest
import WebKit
#if ACODE_FREE
@testable import runnerFree
#else
@testable import runner
#endif

@MainActor
final class PreviewBrowserTests: BridgeTestCase {
    func testUnsavedEditorFileRunsInIsolatedBrowserWithConsoleAndViewportControls() async throws {
        let app = try await appWebView()
        let port = Int.random(in: 49152...60000)
        _ = try await app.callAsyncJavaScript("""
            for(let n=0;!window.editorManager&&n<100;n++) await new Promise(resolve=>setTimeout(resolve,100));
            const settings=acode.require('settings');
            const file=new (acode.require('EditorFile'))('preview-fixture.html',{text:'<!doctype html><title>Preview fixture</title><h1>Unsaved ✓</h1><script>window.consoleShows=0;document.addEventListener("showconsole",()=>consoleShows++);</script>',isUnsaved:true});
            window.previewTestState={file,settings,original:{serverPort:settings.value.serverPort,previewPort:settings.value.previewPort,previewMode:settings.value.previewMode,console:settings.value.console},tutorial:localStorage.__init_runPreview};
            Object.assign(settings.value,{serverPort:port,previewPort:port,previewMode:'inapp',console:settings.CONSOLE_LEGACY});
            localStorage.__init_runPreview='true';
            file.makeActive();
            file.runFile();
            """, arguments: ["port": port], in: nil, contentWorld: .page)
        var responder: UIResponder? = app
        while responder != nil, !(responder is WebViewController) { responder = responder?.next }
        let controller = try XCTUnwrap(responder as? WebViewController)
        do {
            let browser = try await presentedBrowser(controller)
            try await waitForPage(browser.webView, expression: "document.querySelector('h1')?.textContent === 'Unsaved ✓'")
            let isolated = try await browser.webView.evaluateJavaScript("!window.Bridge && !window.webkit?.messageHandlers?.exec") as? Bool
            XCTAssertEqual(isolated, true)
            try await waitForPage(browser.webView, expression: "Boolean(document.querySelector('c-toggler'))")
            browser.setConsoleVisible(true)
            try await waitForPage(browser.webView, expression: "window.consoleShows === 1")
            browser.applyViewport(CGSize(width: 768, height: 1024))
            browser.view.layoutIfNeeded()
            XCTAssertEqual(browser.webView.bounds.size, CGSize(width: 768, height: 1024))
            try await waitForPage(browser.webView, expression: "window.innerWidth === 768")
            XCTAssertFalse(browser.consoleVisible)
            _ = try await browser.webView.callAsyncJavaScript("await document.documentElement.requestFullscreen()", arguments: [:], in: nil, contentWorld: .page)
            for _ in 0..<100 {
                if browser.webView.fullscreenState == .inFullscreen { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            XCTAssertEqual(browser.webView.fullscreenState, .inFullscreen)
            browser.view.setNeedsLayout()
            browser.view.layoutIfNeeded()
            let fullscreenWindow = try XCTUnwrap(browser.webView.window)
            let fullscreenFrame = browser.webView.convert(browser.webView.bounds, to: fullscreenWindow)
            XCTAssertEqual(fullscreenFrame.width, fullscreenWindow.bounds.width, accuracy: 1)
            XCTAssertEqual(fullscreenFrame.height, fullscreenWindow.bounds.height, accuracy: 1)
            _ = try await browser.webView.callAsyncJavaScript("await document.exitFullscreen()", arguments: [:], in: nil, contentWorld: .page)
            for _ in 0..<100 {
                if browser.webView.fullscreenState == .notInFullscreen { break }
                try await Task.sleep(for: .milliseconds(20))
            }
            browser.view.layoutIfNeeded()
            XCTAssertEqual(browser.webView.bounds.size, CGSize(width: 768, height: 1024))
            browser.applyViewport(nil)
            browser.view.layoutIfNeeded()
            XCTAssertEqual(browser.webView.bounds.size, browser.content.bounds.size)
            let window = try XCTUnwrap(browser.view.window)
            let frame = browser.webView.convert(browser.webView.bounds, to: window)
            XCTAssertGreaterThanOrEqual(frame.minY, window.safeAreaInsets.top)
            XCTAssertLessThanOrEqual(frame.maxY, window.bounds.height - window.safeAreaInsets.bottom)
            browser.webView.stopLoading()
            await withCheckedContinuation { continuation in browser.dismiss(animated: false) { continuation.resume() } }
        } catch {
            controller.presentedViewController?.dismiss(animated: false)
            try? await cleanup(app, port: port)
            throw error
        }
        try await cleanup(app, port: port)
    }

    private func presentedBrowser(_ controller: WebViewController) async throws -> PreviewViewController {
        for _ in 0..<100 {
            if let browser = controller.presentedViewController as? PreviewViewController, !browser.isBeingPresented { return browser }
            try await Task.sleep(for: .milliseconds(100))
        }
        throw NSError(domain: "AcodeTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Preview not presented"])
    }

    private func waitForPage(_ webView: WKWebView, expression: String) async throws {
        for _ in 0..<100 {
            if (try? await webView.evaluateJavaScript(expression)) as? Bool == true { return }
            try await Task.sleep(for: .milliseconds(100))
        }
        let contents = try? await webView.evaluateJavaScript("document.body.innerText")
        throw NSError(domain: "AcodeTests", code: 3, userInfo: [NSLocalizedDescriptionKey: "Preview failed: \(expression); \(String(describing: contents))"])
    }

    private func cleanup(_ app: WKWebView, port: Int) async throws {
        _ = try await app.callAsyncJavaScript("""
            const state=window.previewTestState;
            Object.assign(state.settings.value,state.original);
            if(state.tutorial===undefined) delete localStorage.__init_runPreview;
            else localStorage.__init_runPreview=state.tutorial;
            await state.file.remove(true);
            delete window.previewTestState;
            await new Promise(resolve=>Bridge.exec(resolve,resolve,'Server','stop',[port]));
            """, arguments: ["port": port], in: nil, contentWorld: .page)
    }
}
