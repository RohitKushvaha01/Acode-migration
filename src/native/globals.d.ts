import type { NativeBridge } from "./types";
import type { AndroidHost } from "../platforms/android";
import type { IOSReply } from "../platforms/ios";

declare global {
	const Bridge: NativeBridge;
	const __FREE__: boolean;
	const __FDROID__: boolean;
	interface Window {
		toast: typeof import("../components/toast").default;
		Bridge: NativeBridge;
		Android: AndroidHost;
		iOS: { callback(reply: IOSReply): void };
		webkit: {
			messageHandlers: {
				exec: {
					postMessage(message: {
						service: string;
						action: string;
						args: string;
						id: number;
					}): void;
				};
			};
		};
		nativeReady: Promise<void>;
	}
}
