const fs = require("node:fs");
const path = require("node:path");
const ID_PAID = "com.foxdebug.acode";
const ID_FREE = "com.foxdebug.acodefree";

module.exports = { getAppConfig };

function getAppConfig() {
	const { name } = JSON.parse(
		fs.readFileSync(path.resolve(__dirname, "../package.json"), "utf8"),
	);
	if (![ID_PAID, ID_FREE].includes(name)) {
		throw new Error(
			`Set package.json name to ${ID_PAID} (paid) or ${ID_FREE} (free).`,
		);
	}
	return { variant: name === ID_FREE ? "free" : "paid", targetId: name };
}
