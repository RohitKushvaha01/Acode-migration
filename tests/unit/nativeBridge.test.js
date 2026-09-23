import fs from "node:fs";
import { createRequire } from "node:module";
import { Window } from "happy-dom";
import os from "node:os";
import path from "node:path";
import { beforeAll, afterAll, afterEach, describe, expect, test } from "vitest";

const require = createRequire(import.meta.url);
const { rspack } = require("@rspack/core");
const directory = fs.mkdtempSync(path.join(os.tmpdir(), "acode-native-bundles-"));
const bundles = new Map();
beforeAll(async () => {
    for (const [variant, fdroid] of [["paid", false], ["free", false], ["paid", true]]) {
        const filename = `${variant}-${fdroid}.js`;
        const compiler = rspack({
            mode: "development",
            devtool: false,
            entry: path.resolve("src/native/index.ts"),
            output: { path: directory, filename },
            resolve: { extensions: [".js", ".ts"] },
            module: { rules: [
                { test: /\.ts$/, loader: "builtin:swc-loader", options: { jsc: { parser: { syntax: "typescript" }, target: "es2015" } } },
                { test: /\.js$/, type: "javascript/auto", loader: "builtin:swc-loader", options: { jsc: { parser: { syntax: "ecmascript" }, target: "es2015" } } },
            ] },
            plugins: [new rspack.DefinePlugin({ __FREE__: JSON.stringify(variant === "free"), __FDROID__: JSON.stringify(fdroid) })],
        });
        await new Promise((resolve, reject) => compiler.run((error, stats) => compiler.close(() => {
            if (error || stats.hasErrors()) reject(error || new Error(stats.toString()));
            else resolve();
        })));
        bundles.set(filename, fs.readFileSync(path.join(directory, filename), "utf8"));
    }
}, 30000);
afterAll(() => fs.rmSync(directory, { recursive: true, force: true }));
const windows = [];
afterEach(() => { for (const window of windows.splice(0)) window.happyDOM.abort(); });

describe("Acode bridge", () => {
	test("initializes the actual vendored modules before device readiness and replays readiness to late listeners", async () => {
		const { window, pending } = await createBridge();
		const events = [];
		window.document.addEventListener("deviceready", () => events.push("ready"));
		expect(events).toEqual([]);
		for (const request of pending.splice(0)) {
			const data = request.service === "Device" ? { platform: "Android", sdkVersion: 36 }
				: request.service === "File" ? { dataDirectory: "file:///data/user/0/com.foxdebug.acode/files/", applicationDirectory: "file:///android_asset/" }
				: {};
			window.Android.callback({ id: request.id, status: 1, keep: false, data });
		}
		window.document.dispatchEvent(new window.Event("DOMContentLoaded"));
		await new Promise(resolve => setTimeout(resolve, 0));
		expect(events).toEqual(["ready"]);
		expect(window.earlyReadyCount).toBe(1);
		window.document.addEventListener("deviceready", () => events.push("late"));
		expect(events).toEqual(["ready", "late"]);
		expect(window.Bridge.file.dataDirectory).toContain("com.foxdebug.acode/files/");
		expect(window.device.sdkVersion).toBe(36);
		expect(typeof window.resolveLocalFileSystemURL).toBe("function");
		expect(typeof window.Bridge.http.sendRequest).toBe("function");
		expect(window.FileReader).not.toBe(window.browserFileReader);
	});

	test("initializes free and F-Droid bundles with the expected native globals", async () => {
        const free = (await createBridge("free")).window;
        expect(typeof free.admob.BannerAd).toBe("function");
        expect(typeof free.iap).not.toBe("undefined");
        const fdroid = (await createBridge("paid", true)).window;
        expect(fdroid.admob).toBeUndefined();
        expect(fdroid.iap).toBeUndefined();
        expect(typeof fdroid.resolveLocalFileSystemURL).toBe("function");
    });

    test("preserves system theme callbacks and automatic hardware-button subscriptions", async () => {
        const { window, pending } = await createBridge();
        // Happy DOM does not normalize CSS hex colors to computed RGB as WebView does.
        window.getComputedStyle = () => ({ getPropertyValue: () => "rgb(17, 34, 51)" });
        let themed = false;
        window.system.setUiTheme("#112233", {}, () => { themed = true; });
        const request = pending.at(-1);
        expect(request.service).toBe("System");
        window.Android.callback({ id: request.id, status: 1, keep: false, data: null });
        expect(themed).toBe(true);
        expect(pending.at(-1).service).toBe("SystemBarPlugin");
        expect(pending.at(-1).action).toBe("setStatusBarBackgroundColor");
        const events = [];
        const listener = () => events.push("volume");
        window.document.addEventListener("volumeupbutton", listener);
        expect(JSON.parse(pending.at(-1).args)).toEqual(["volumeupbutton", true]);
        window.Bridge.fireDocumentEvent("volumeupbutton");
        expect(events).toEqual(["volume"]);
        window.document.removeEventListener("volumeupbutton", listener);
        expect(JSON.parse(pending.at(-1).args)).toEqual(["volumeupbutton", false]);
    });

	test("registers synchronous callbacks first and retains streaming callbacks until completion", async () => {
		const { window } = await createBridge();
		const received = [];
		window.Android.exec = (service, action, args, id) => {
			window.Android.callback({ id, status: 1, keep: true, data: "chunk" });
			window.Android.callback({ id, status: 1, keep: false, data: "done" });
			window.Android.callback({ id, status: 1, keep: false, data: "stale" });
			return true;
		};
		window.Bridge.exec((value) => received.push(value), null, "System", "stream", []);
		expect(received).toEqual(["chunk", "done"]);
	});

	test("preserves empty errors, binary buffers and multipart results", async () => {
		const { window, pending } = await createBridge();
		const errors = [];
		const values = [];
		window.Bridge.exec(null, (error) => errors.push(error), "File", "read", []);
		window.Android.callback({ id: pending.at(-1).id, status: 9, keep: false, data: "" });
		expect(errors).toEqual([""]);
		window.Bridge.exec((...data) => values.push(data), null, "File", "read", []);
		window.Android.callback({ id: pending.at(-1).id, status: 1, keep: false, data: { kind: "multipart", data: [{ kind: "arrayBuffer", data: "AP8=" }, 2] } });
		expect([...new Uint8Array(values[0][0])]).toEqual([0, 255]);
		expect(values[0][1]).toBe(2);
	});

	test("excludes advertising from paid and billing from F-Droid bridges", async () => {
        expect((await createBridge("paid")).window.admob).toBeUndefined();
        expect(typeof (await createBridge("free")).window.admob.BannerAd).toBe("function");
        expect((await createBridge("paid", true)).window.iap).toBeUndefined();
        expect(bundles.get("paid-false.js")).not.toContain("./src/native/admob/admob.ts");
        expect(bundles.get("paid-true.js")).not.toContain("./src/native/iap.ts");
	});
});

describe("typed native API behavior", () => {
    test("loads polyfills before native modules on older WebViews", async () => {
        const { window } = await createBridge("paid", false, "android", true);
        expect(typeof window.Object.fromEntries).toBe("function");
        expect(window.Bridge.file).toHaveProperty("dataDirectory");
    });
    test("reads file slices across chunks and ignores results after abort", async () => {
        const { window, pending } = await createBridge();
        window.FileReader.READ_CHUNK_SIZE = 3;
        const file = new window.File("test.txt", "file:///test.txt", "text/plain", 0, 8).slice(1, -1);
        const reader = new window.FileReader();
        const events = [];
        for (const type of ["loadstart", "progress", "load", "loadend", "abort"])
            reader[`on${type}`] = event => events.push([type, event.loaded]);
        reader.readAsText(file);
        expect(JSON.parse(pending.at(-1).args)).toEqual([file.localURL, "UTF-8", 1, 4]);
        respond(window, pending.at(-1), "abc");
        expect(JSON.parse(pending.at(-1).args)).toEqual([file.localURL, "UTF-8", 4, 7]);
        respond(window, pending.at(-1), "def");
        expect(reader.result).toBe("abcdef");
        expect(reader.readyState).toBe(window.FileReader.DONE);
        expect(events.map(([type]) => type)).toEqual(["loadstart", "progress", "progress", "load", "loadend"]);
        reader.readAsText(file);
        const aborted = pending.at(-1);
        reader.abort();
        respond(window, aborted, "late");
        expect(reader.result).toBeNull();
        expect(events.slice(-2).map(([type]) => type)).toEqual(["abort", "loadend"]);
    });

    test("delegates Blob reads and preserves writer events, binary data, seeking and truncation", async () => {
        const { window, pending } = await createBridge();
        const reader = new window.FileReader();
        const read = new Promise((resolve, reject) => { reader.onload = () => resolve(reader.result); reader.onerror = reject; });
        reader.readAsText(new window.Blob(["Unicode ✓"]));
        expect(await read).toBe("Unicode ✓");
        const writer = new window.FileWriter(new window.File("test", "file:///test", null, 0, 12));
        const events = [];
        for (const type of ["writestart", "write", "writeend"]) writer[`on${type}`] = () => events.push(type);
        writer.seek(-2);
        writer.write(window.eval("new Uint8Array([0, 255]).buffer"));
        expect(JSON.parse(pending.at(-1).args)).toEqual(["file:///test", "AP8=", 10, true]);
        respond(window, pending.at(-1), 2);
        expect([writer.position, writer.length]).toEqual([12, 12]);
        writer.truncate(4);
        respond(window, pending.at(-1), 4);
        expect([writer.position, writer.length]).toEqual([4, 4]);
        expect(events).toEqual(["writestart", "write", "writeend", "writestart", "write", "writeend"]);
        expect(new window.FileUploadResult(12, 201, "uploaded")).toMatchObject({ bytesSent: 12, responseCode: 201, response: "uploaded" });
    });

    test("keeps file URL mapping and one-shot directory readers", async () => {
        const { window, pending } = await createBridge();
        const fs = new window.FileSystem("files");
        const file = new window.FileEntry("a #.txt", "/a #.txt", fs, "file:///private/a%20%23.txt");
        expect(file.toInternalURL()).toBe("https://localhost/__cdvfile_files__/a%20%23.txt");
        const content = new window.FileEntry("test", "/ignored", new window.FileSystem("content"), "content://provider/tree/a%3Ab/document/a%3Ab%2Ftest");
        expect(content.toInternalURL()).toBe("https://localhost/__cdvfile_content__/provider/tree/a%3Ab/document/a%3Ab%2Ftest");
        const reader = fs.root.createReader();
        const batches = [];
        reader.readEntries(entries => batches.push(entries));
        respond(window, pending.at(-1), [{ name: "folder", fullPath: "/folder/", nativeURL: "file:///folder/", isDirectory: true, filesystemName: "files" }]);
        const count = pending.length;
        reader.readEntries(entries => batches.push(entries));
        expect(batches[0][0].isDirectory).toBe(true);
        expect(batches[1]).toEqual([]);
        expect(pending.length).toBe(count);
    });

    test("retains stored cookies, scopes paths and domains, and accepts expiry commas", async () => {
        const { window } = await createBridge();
        const http = window.Bridge.http;
        window.localStorage.setItem("__advancedHttpCookieStore__", JSON.stringify({ "example.com": { "/": { legacy: { key: "legacy", value: "retained", domain: "example.com", path: "/", hostOnly: true, creation: "2024-01-01T00:00:00.000Z", lastAccessed: "2024-01-01T00:00:00.000Z" } } } }));
        expect(http.getCookieString("https://example.com/")).toBe("legacy=retained");
        http.setCookie("https://example.com/private", "session=secret; Path=/private; Secure");
        http.setCookieFromString("https://example.com/", "future=yes; Expires=Wed, 01 Jan 2031 00:00:00 GMT, second=two; Path=/");
        expect(http.getCookieString("https://example.com/private")).toContain("session=secret");
        expect(http.getCookieString("http://example.com/private")).not.toContain("session=");
        expect(http.getCookieString("https://other.example.com/")).toBe("");
        expect(http.getCookieString("https://example.com/")).toContain("future=yes");
        expect(http.getCookieString("https://example.com/")).toContain("second=two");
        http.setCookie("https://example.com/", "legacy=; Max-Age=0; Path=/");
        expect(http.getCookieString("https://example.com/")).not.toContain("legacy=");
        await new Promise((resolve, reject) => http.removeCookies("https://example.com/", error => error ? reject(error) : resolve()));
        expect(http.getCookieString("https://example.com/private")).toBe("");
        // Requests to preview servers must also work when there are no cookies.
        expect(http.getCookieString("http://localhost:48123/")).toBe("");
        expect(http.getCookieString("http://127.0.0.1:48123/")).toBe("");
    });

    test("sends HTTP options and headers, decodes responses and keeps cookies on failures", async () => {
        const { window, pending } = await createBridge();
        const http = window.Bridge.http;
        const received = [], failures = [];
        http.setHeader("X-Global", "global");
        http.setHeader("example.com", "X-Host", "host");
        http.setCookie("https://example.com/", "token=one; Path=/");
        const id = http.sendRequest("https://example.com/path", { responseType: "json", params: { q: "a b" }, followRedirect: false, connectTimeout: 0, readTimeout: 9, headers: { "X-Global": "request" } }, value => received.push(value), value => failures.push(value));
        expect(JSON.parse(pending.at(-1).args)).toEqual(["https://example.com/path?q=a%20b", { "X-Global": "request", "X-Host": "host", Cookie: "token=one" }, 0, 9, false, "json", id]);
        respond(window, pending.at(-1), { status: 200, url: "https://example.com/path", data: '{"ok":true}', headers: {} });
        expect(received[0].data).toEqual({ ok: true });
        http.sendRequest("https://example.com/", { responseType: "json" }, value => received.push(value), value => failures.push(value));
        respond(window, pending.at(-1), { status: 200, url: "https://example.com/", data: "invalid json", headers: {} });
        expect(failures[0].status).toBe(http.ErrorCode.POST_PROCESSING_FAILED);
        http.get("https://example.com/", {}, {}, () => {}, value => failures.push(value));
        respond(window, pending.at(-1), { status: 401, url: "https://example.com/", error: "denied", headers: { "Set-Cookie": "token=two; Path=/" } }, 9);
        expect(http.getCookieString("https://example.com/")).toBe("token=two");
        http.abort(id, () => {}, () => {});
        expect([pending.at(-1).action, JSON.parse(pending.at(-1).args)]).toEqual(["abort", [id]]);
    });

    test("encodes multipart and binary HTTP bodies and returns download entries", async () => {
        const { window, pending } = await createBridge();
        const http = window.Bridge.http;
        const form = new http.ponyfills.FormData();
        form.append("name", "✓");
        form.append("file", new window.Blob(["hello"], { type: "text/plain" }), "hello.txt");
        http.sendRequest("https://example.com/", { method: "post", serializer: "multipart", data: form }, () => {}, error => { throw error; });
        await window.happyDOM.waitUntilComplete();
        const args = JSON.parse(pending.at(-1).args);
        expect(args[1]).toEqual({ buffers: ["4pyT", "aGVsbG8="], names: ["name", "file"], fileNames: [null, "hello.txt"], types: ["text/plain", "text/plain"] });
        http.sendRequest("https://example.com/", { method: "post", serializer: "raw", data: window.eval("new Uint8Array([0,255])") }, () => {}, () => {});
        expect(JSON.parse(pending.at(-1).args)[1]).toBe("AP8=");
        const download = new Promise((resolve, reject) => http.downloadFile("https://example.com/file", {}, {}, "file:///cache/file", (...values) => resolve(values), reject));
        respond(window, pending.at(-1), { status: 200, url: "https://example.com/file", headers: {}, file: { name: "file", fullPath: "/file", filesystemName: "cache", nativeURL: "file:///cache/file", isDirectory: false } });
        const [entry, response] = await download;
        expect(entry.toInternalURL()).toBe("https://localhost/__cdvfile_cache__/file");
        expect(response.file).toBe(entry);
    });

    test("registers native WebSocket events and preserves binary views and close state", async () => {
        const { window, pending } = await createBridge();
        const connection = window.Bridge.websocket.connect("ws://localhost/test", ["test"], { Authorization: "token" }, "arraybuffer");
        expect(JSON.parse(pending.at(-1).args)).toEqual(["ws://localhost/test", ["test"], { Authorization: "token" }, "arraybuffer"]);
        respond(window, pending.at(-1), "socket-1");
        const socket = await connection;
        const listener = pending.at(-1);
        expect(listener.action).toBe("registerListener");
        window.Android.callback({ id: listener.id, status: 1, keep: true, data: { type: "open" } });
        const messages = [];
        socket.addEventListener("message", event => messages.push(event.data));
        window.Android.callback({ id: listener.id, status: 1, keep: true, data: { type: "message", data: "AP8=", isBinary: true } });
        expect([...new Uint8Array(messages[0])]).toEqual([0, 255]);
        socket.send(window.eval("new Uint8Array([1,2,3,4]).subarray(1,3)"));
        expect(JSON.parse(pending.at(-1).args)).toEqual(["socket-1", "AgM=", true]);
        socket.close(1000, "done");
        expect(socket.readyState).toBe(2);
        const closes = [];
        socket.addEventListener("close", event => closes.push([event.code, event.reason]));
        window.Android.callback({ id: listener.id, status: 1, keep: false, data: { type: "close", data: JSON.stringify({ code: 1000, reason: "done" }) } });
        expect(closes).toEqual([[1000, "done"]]);
        expect(socket.readyState).toBe(3);
        expect(() => socket.send("late")).toThrow("not open");
    });

    test("routes iOS callbacks and toast through the retained platform adapter", async () => {
        const { window, pending } = await createBridge("paid", false, "ios");
        expect(window.Bridge.platformId).toBe("ios");
        const values = [], errors = [];
        window.Bridge.exec(value => values.push(value), error => errors.push(error), "Native", "test", []);
        const id = pending.at(-1).id;
        window.iOS.callback({ id, keep: true, success: "\0ÿ", isBinary: true, length: 2 });
        window.iOS.callback({ id, error: "" });
        window.iOS.callback({ id, success: "stale" });
        expect([...new Uint8Array(values[0])]).toEqual([0, 255]);
        expect(errors).toEqual([""]);
        const toasts = [];
        window.toast = value => toasts.push(value);
        window.Bridge.exec(() => values.push("toast complete"), null, "Native", "showToast", ["hello"]);
        expect(toasts).toEqual(["hello"]);
        expect(values.at(-1)).toBe("toast complete");
    });
});

function respond(window, request, data, status = 1) {
    window.Android.callback({ id: request.id, status, keep: false, data });
}

async function createBridge(variant = "paid", fdroid = false, platform = "android", missingFromEntries = false) {
	const window = new Window({ url: "https://localhost/index.html" });
	// Chromium exposes these FileSystem constants as read-only Window properties.
	Object.defineProperty(window, "TEMPORARY", { value: 0, writable: false });
	Object.defineProperty(window, "PERSISTENT", { value: 1, writable: false });
	windows.push(window);
	const pending = [];
	window.browserFileReader = window.FileReader;
	window.earlyReadyCount = 0;
	window.document.addEventListener("deviceready", () => window.earlyReadyCount++);
	if (platform === "android") window.Android = { exec(service, action, args, id) { pending.push({ service, action, args, id }); return true; } };
    else window.webkit = { messageHandlers: { exec: { postMessage(request) { pending.push(request); } } } };
	if (missingFromEntries) window.eval("Object.fromEntries = undefined");
	window.eval(bundles.get(`${variant}-${fdroid}.js`));
	await window.nativeReady;
	return { window, pending };
}
