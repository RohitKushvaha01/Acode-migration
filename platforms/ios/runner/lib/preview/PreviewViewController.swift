import UIKit
import WebKit

@MainActor
final class PreviewViewController: UIViewController {
    let webView: WKWebView
    let initialURL: URL
    let consoleOnly: Bool
    let theme: [String: Any]
    let address = UITextField()
    let content = UIView()
    let toolbar = UIToolbar()
    let progress = UIProgressView(progressViewStyle: .bar)
    var backButton: UIBarButtonItem!
    var forwardButton: UIBarButtonItem!
    var menuButton: UIBarButtonItem!
    var consoleVisible = false
    var consoleAvailable = false
    var disableCache = false
    var showTools = true
    var allowsDownloads = true
    var externalSchemesAllowed = true
    var navigationPolicy: ((WKNavigationAction) -> Bool)?
    var onPageFinished: (() -> Void)?
    var onTitleChanged: ((String) -> Void)?
    var consoleEnabled = true
    var viewport: CGSize?
    var viewportScale: CGFloat = 1
    var onExternal: ((URL) -> Void)?
    var onClose: (() -> Void)?
    var pendingDialog: (() -> Void)?
    weak var auxiliaryPresenter: UIViewController?
    weak var activeDialog: UIAlertController?
    private(set) var isClosed = false
    private var observations: [NSKeyValueObservation] = []

    init(url: URL, theme: [String: Any], console: Bool) {
        initialURL = url
        self.theme = theme
        consoleOnly = console
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WKWebsiteDataStore(forIdentifier: UUID(uuidString: "A0C0DE00-0000-4000-8000-000000000001")!)
        configuration.allowsInlineMediaPlayback = true
        configuration.preferences.isElementFullscreenEnabled = true
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        let background = (theme["primaryColor"] as? String).flatMap(UIColor.init(hexString:)) ?? .systemBackground
        let foreground = (theme["primaryTextColor"] as? String).flatMap(UIColor.init(hexString:)) ?? .label
        view.backgroundColor = background
        if theme["type"] as? String == "dark" { overrideUserInterfaceStyle = .dark }
        if theme["type"] as? String == "light" { overrideUserInterfaceStyle = .light }
        view.tintColor = foreground
        content.clipsToBounds = true
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        #if DEBUG
        webView.isInspectable = true
        #endif
        address.delegate = self
        address.text = initialURL.absoluteString
        address.textColor = foreground
        address.font = .preferredFont(forTextStyle: .subheadline)
        address.keyboardType = .URL
        address.returnKeyType = .go
        address.autocapitalizationType = .none
        address.autocorrectionType = .no
        address.clearButtonMode = .whileEditing
        address.isEnabled = !consoleOnly
        address.accessibilityLabel = "Preview address"
        backButton = UIBarButtonItem(image: UIImage(systemName: "chevron.left"), style: .plain, target: self, action: #selector(goBack))
        backButton.accessibilityLabel = "Back"
        forwardButton = UIBarButtonItem(image: UIImage(systemName: "chevron.right"), style: .plain, target: self, action: #selector(goForward))
        forwardButton.accessibilityLabel = "Forward"
        menuButton = UIBarButtonItem(image: UIImage(systemName: "ellipsis.circle"), style: .plain, target: nil, action: nil)
        menuButton.accessibilityLabel = "Preview menu"
        let refresh = UIBarButtonItem(barButtonSystemItem: .refresh, target: self, action: #selector(refresh))
        let close = UIBarButtonItem(barButtonSystemItem: .close, target: self, action: #selector(close))
        toolbar.items = consoleOnly || !showTools ? [close, .flexibleSpace(), refresh] : [close, backButton, forwardButton, .flexibleSpace(), refresh, menuButton]
        let appearance = UIToolbarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = background
        toolbar.standardAppearance = appearance
        toolbar.scrollEdgeAppearance = appearance
        for child in [toolbar, address, content, progress] { child.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(child) }
        content.addSubview(webView)
        NSLayoutConstraint.activate([
            toolbar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            toolbar.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            toolbar.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor), toolbar.heightAnchor.constraint(equalToConstant: 44),
            address.topAnchor.constraint(equalTo: toolbar.bottomAnchor), address.heightAnchor.constraint(equalToConstant: 36),
            address.leadingAnchor.constraint(equalTo: toolbar.leadingAnchor, constant: 12), address.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor, constant: -12),
            progress.topAnchor.constraint(equalTo: address.bottomAnchor), progress.leadingAnchor.constraint(equalTo: toolbar.leadingAnchor), progress.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor),
            content.topAnchor.constraint(equalTo: progress.bottomAnchor), content.leadingAnchor.constraint(equalTo: toolbar.leadingAnchor),
            content.trailingAnchor.constraint(equalTo: toolbar.trailingAnchor), content.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor),
        ])
        observations = [
            webView.observe(\.estimatedProgress, options: [.initial, .new]) { [weak self] webView, _ in
                MainActor.assumeIsolated { self?.progress.progress = Float(webView.estimatedProgress); self?.progress.isHidden = webView.estimatedProgress == 1 }
            },
            webView.observe(\.canGoBack, options: [.initial, .new]) { [weak self] view, _ in MainActor.assumeIsolated { self?.backButton.isEnabled = view.canGoBack } },
            webView.observe(\.canGoForward, options: [.initial, .new]) { [weak self] view, _ in MainActor.assumeIsolated { self?.forwardButton.isEnabled = view.canGoForward } },
            webView.observe(\.title, options: [.new]) { [weak self] view, _ in MainActor.assumeIsolated { self?.onTitleChanged?(view.title ?? "") } },
            webView.observe(\.fullscreenState, options: [.new]) { [weak self] view, _ in
                MainActor.assumeIsolated { if view.fullscreenState == .notInFullscreen { self?.view.setNeedsLayout() } }
            },
        ]
        updateMenu()
        load(initialURL)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard webView.superview === content else { return }
        let size = viewport ?? content.bounds.size
        let scale = viewport == nil ? 1 : min(content.bounds.width / max(size.width, 1), content.bounds.height / max(size.height, 1)) * viewportScale
        webView.transform = .identity
        webView.bounds = CGRect(origin: .zero, size: size)
        webView.center = CGPoint(x: content.bounds.midX, y: content.bounds.midY)
        webView.transform = CGAffineTransform(scaleX: scale, y: scale)
    }

    func load(_ url: URL) {
        guard ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { return }
        prepareNavigation { [weak self] in
            guard let self else { return }
            self.webView.load(URLRequest(url: url, cachePolicy: self.disableCache ? .reloadIgnoringLocalCacheData : .useProtocolCachePolicy))
        }
    }

    func prepareNavigation(_ action: @escaping @MainActor @Sendable () -> Void) {
        guard !isClosed else { return }
        if disableCache {
            webView.configuration.websiteDataStore.removeData(ofTypes: [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache, WKWebsiteDataTypeFetchCache], modifiedSince: .distantPast) { [weak self] in
                guard let self, !self.isClosed else { return }
                action()
            }
        } else { action() }
    }

    var dialogPresenter: UIViewController? { isClosed ? nil : (isViewLoaded && view.window != nil ? self : auxiliaryPresenter) }

    @objc func close() {
        isClosed = true
        pendingDialog?(); pendingDialog = nil
        let dialog = activeDialog; activeDialog = nil
        webView.stopLoading()
        let completion = onClose; onClose = nil
        if let presenter = presentingViewController { presenter.dismiss(animated: true) { completion?() } }
        else if let dialog, dialog.presentingViewController != nil { dialog.dismiss(animated: false) { completion?() } }
        else { completion?() }
    }
}
