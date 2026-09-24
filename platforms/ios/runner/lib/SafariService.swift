import SafariServices

final class SafariService: BaseService {
    private weak var browser: SFSafariViewController?

    override func reset() {
        DispatchQueue.main.async { self.browser?.dismiss(animated: false); self.browser = nil }
    }

    override func exec(action: String, args: [Any], callback: Callback) {
        guard let value = args[safe: 0] as? String, let url = URL(string: value), let scheme = url.scheme?.lowercased() else {
            callback.error("Invalid browser URL"); return
        }
        if action == "external" {
            guard !["javascript", "data", "file", "acode", "about"].contains(scheme) else { callback.error("Unsupported external URL"); return }
            DispatchQueue.main.async {
                UIApplication.shared.open(url) { opened in
                    if opened { callback.success() }
                    else { callback.error("No app can open this URL") }
                }
            }
            return
        }
        guard action == "open", ["http", "https"].contains(scheme), url.host != nil else {
            callback.error("Custom tabs require an HTTP or HTTPS URL"); return
        }
        let options = args[safe: 1] as? [String: Any] ?? [:]
        DispatchQueue.main.async {
            guard let presenter = self.viewController, presenter.presentedViewController == nil else {
                callback.error("A browser cannot be presented right now"); return
            }
            let browser = SFSafariViewController(url: url)
            browser.dismissButtonStyle = .close
            if #unavailable(iOS 26), let color = options["toolbarColor"] as? String { browser.preferredBarTintColor = UIColor(hexString: color) }
            self.browser = browser
            presenter.present(browser, animated: true) { callback.success() }
        }
    }
}
