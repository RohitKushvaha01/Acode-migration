import { createRequire } from "node:module";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

const require = createRequire(import.meta.url);
const { adUnits } = require("../../dev/scripts/iosAds.js");
const banner = { on: vi.fn() };
const interstitial = { load: vi.fn().mockResolvedValue(), on: vi.fn() };

vi.mock("../../src/lib/bannerVisibilityController.mjs", () => ({
	BANNER_SUPPRESSION_REASON: {},
	bannerVisibilityController: { setBanner: vi.fn() },
}));
vi.mock("../../src/lib/config", () => ({ default: { HAS_PRO: false } }));

beforeEach(() => {
	vi.resetModules();
	vi.stubGlobal("window", { ANDROID_SDK_INT: 0 });
	vi.stubGlobal("Bridge", { platformId: "ios" });
	vi.stubGlobal("BuildInfo", { buildType: "debug" });
	vi.stubGlobal("__IOS_AD_UNITS__", adUnits("Debug"));
	vi.stubGlobal("admob", {
		privacy: {
			gatherConsent: vi
				.fn()
				.mockResolvedValue({
					consentStatus: "notRequired",
					canRequestAds: true,
				}),
		},
		start: vi.fn().mockResolvedValue(),
		configure: vi.fn().mockResolvedValue(),
		BannerAd: vi.fn(function () {
			return banner;
		}),
		InterstitialAd: vi.fn(function () {
			return interstitial;
		}),
	});
});
afterEach(() => {
	vi.unstubAllGlobals();
	vi.unstubAllEnvs();
	vi.clearAllMocks();
});

test("iOS initializes after consent using iOS IDs without the Android SDK gate", async () => {
	const { default: startAd, adUnitIdRewarded } = await import(
		"../../src/lib/startAd.js"
	);
	await startAd();
	expect(admob.privacy.gatherConsent).toHaveBeenCalledOnce();
	expect(admob.start).toHaveBeenCalledOnce();
	expect(admob.BannerAd).toHaveBeenCalledWith({
		adUnitId: adUnits("Debug").banner,
		position: "bottom",
	});
	expect(admob.InterstitialAd).toHaveBeenCalledWith({
		adUnitId: adUnits("Debug").interstitial,
	});
	expect(adUnitIdRewarded).toBe(adUnits("Debug").rewarded);
	expect(window.adRewardedUnitId).toBe(adUnitIdRewarded);
});

test("iOS does not initialize ads when consent is unavailable", async () => {
	admob.privacy.gatherConsent.mockResolvedValue({
		consentStatus: "required",
		canRequestAds: false,
	});
	const { default: startAd } = await import("../../src/lib/startAd.js");
	await startAd();
	expect(admob.start).not.toHaveBeenCalled();
	expect(admob.BannerAd).not.toHaveBeenCalled();
});

test("Android retains its existing test units and SDK version gate", async () => {
	Bridge.platformId = "android";
	window.ANDROID_SDK_INT = 28;
	const { default: startAd } = await import("../../src/lib/startAd.js");
	await startAd();
	expect(admob.start).not.toHaveBeenCalled();
	window.ANDROID_SDK_INT = 29;
	await startAd();
	expect(admob.BannerAd).toHaveBeenCalledWith({
		adUnitId: "ca-app-pub-3940256099942544/6300978111",
		position: "bottom",
	});
	expect(window.adRewardedUnitId).toBe(
		"ca-app-pub-3940256099942544/5224354917",
	);
});

test("free release builds require dedicated production IDs and reject demo IDs", () => {
	for (const format of ["BANNER", "INTERSTITIAL", "REWARDED"])
		vi.stubEnv(`ACODE_IOS_ADMOB_${format}_ID`, "");
	expect(() => adUnits("Release")).toThrow(/ACODE_IOS_ADMOB_BANNER_ID/);
	vi.stubEnv("ACODE_IOS_ADMOB_BANNER_ID", adUnits("Debug").banner);
	expect(() => adUnits("Release")).toThrow(/ACODE_IOS_ADMOB_BANNER_ID/);
	for (const format of ["BANNER", "INTERSTITIAL", "REWARDED"]) {
		vi.stubEnv(
			`ACODE_IOS_ADMOB_${format}_ID`,
			"ca-app-pub-1111111111111111/1111111111",
		);
	}
	expect(Object.values(adUnits("Release"))).toEqual(
		Array(3).fill("ca-app-pub-1111111111111111/1111111111"),
	);
});

test("a stale web bundle does not crash iOS startup or request Android ads", async () => {
	vi.stubGlobal("__IOS_AD_UNITS__", null);
	const error = vi.spyOn(console, "error").mockImplementation(() => {});
	try {
		const { default: startAd } = await import("../../src/lib/startAd.js");
		await startAd();
		expect(admob.start).not.toHaveBeenCalled();
		expect(error).toHaveBeenCalledWith(
			"Failed to initialize ads:",
			expect.objectContaining({
				message: expect.stringContaining("Rebuild the iOS"),
			}),
		);
	} finally {
		error.mockRestore();
	}
});
