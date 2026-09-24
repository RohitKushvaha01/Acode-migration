const { spawnSync } = require("node:child_process");
const path = require("node:path");
const args = process.argv.slice(2);
const platform = args.includes("ios") ? "ios" : "android";
const result = spawnSync(
	process.execPath,
	[path.join(__dirname, `${platform}.js`), ...args],
	{ stdio: "inherit" },
);
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
