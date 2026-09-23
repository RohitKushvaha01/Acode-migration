import type { NativeCallback } from "../../native/bridge";

type ProxyAction = (
	success: NativeCallback,
	error: NativeCallback,
	args: unknown[],
) => void;

const proxy: Record<string, Record<string, ProxyAction>> = {
	Native: {
		showToast(success, error, args) {
			const [message] = args;
			if (typeof message !== "string") {
				error?.(new Error("message required"));
				return;
			}
			window.toast(message);
			success?.();
		},
	},
};

export default proxy;
