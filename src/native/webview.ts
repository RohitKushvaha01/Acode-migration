import bridge from "./bridge";
import exec from "./exec";

const call = bridge("AcodeWebView");
type MessageListener = (payload: {
	id: string;
	event?: string;
	data?: unknown;
	message?: unknown;
}) => void;
let messageCallback: MessageListener | null = null;
export default {
	setMessageCallback(callback: MessageListener) {
		messageCallback = callback;
		exec(
			(payload) => messageCallback?.(payload),
			(error) => console.error("WebView message callback error:", error),
			"AcodeWebView",
			"setMessageCallback",
		);
	},
	create: (options: Record<string, unknown> = {}) =>
		call<string>("create", [options]),
	loadURL: (id: string, url: string) => call("loadURL", [id, url]),
	loadHTML: (id: string, html: string) => call("loadHTML", [id, html]),
	evaluate: (id: string, code: string) => call<unknown>("evaluate", [id, code]),
	postMessage: (id: string, message: unknown) =>
		call("postMessage", [
			id,
			typeof message === "string" ? message : JSON.stringify(message),
		]),
	show: (id: string) => call("show", [id]),
	hide: (id: string) => call("hide", [id]),
	reload: (id: string) => call("reload", [id]),
	destroy: (id: string) => call("destroy", [id]),
};
