import XCTest
import WebKit
#if ACODE_FREE
@testable import runnerFree
#else
@testable import runner
#endif

@MainActor
final class AdsEditionTests: BridgeTestCase {
    func testEditionIncludesOnlyItsAdvertisingImplementation() async throws {
        let webView = try await appWebView()
        let included = try await webView.evaluateJavaScript("typeof window.admob !== 'undefined'") as? Bool
        let flavor = try await webView.evaluateJavaScript("BuildInfo.flavor") as? String
        #if ACODE_FREE
        XCTAssertEqual(included, true)
        XCTAssertEqual(flavor, "free")
        XCTAssertNotNil(NSClassFromString("GADMobileAds"))
        XCTAssertNotNil(NSClassFromString("UMPConsentInformation"))
        XCTAssertTrue((Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String)?.hasPrefix("ca-app-pub-") == true)
        XCTAssertNotNil(Bundle.main.url(forResource: "AMNAdView", withExtension: "nib"))
        #else
        XCTAssertEqual(included, false)
        XCTAssertEqual(flavor, "paid")
        XCTAssertNil(NSClassFromString("GADMobileAds"))
        XCTAssertNil(NSClassFromString("UMPConsentInformation"))
        XCTAssertNil(Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier"))
        XCTAssertNil(Bundle.main.url(forResource: "AMNAdView", withExtension: "nib"))
        let rejected = try await webView.callAsyncJavaScript("""
            return await new Promise(resolve=>Bridge.exec(()=>resolve(false),
                error=>resolve(error.code==='UNSUPPORTED_SERVICE'),'AdMob','start',[]));
            """, arguments: [:], in: nil, contentWorld: .page) as? Bool
        XCTAssertEqual(rejected, true)
        #endif
    }
}
