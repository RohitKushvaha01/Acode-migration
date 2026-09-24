import WebKit

final class AppAPIHandler {
    private let origin: URL
    private let auth: AppAuthentication
    private var requests: [ObjectIdentifier: HTTPTransport] = [:]

    init(origin: URL = URL(string: AppAuthentication.origin)!, auth: AppAuthentication = AppAuthentication()) {
        self.origin = origin
        self.auth = auth
    }

    func start(_ task: WKURLSchemeTask) {
        let id = ObjectIdentifier(task)
        do {
            guard let source = task.request.url, source.scheme == "acode", source.host == "localhost", source.path.hasPrefix("/__api__/") else { throw URLError(.badURL) }
            var destination = URLComponents(url: origin, resolvingAgainstBaseURL: false)!
            let incoming = URLComponents(url: source, resolvingAgainstBaseURL: false)!
            destination.percentEncodedPath = "/api/" + incoming.percentEncodedPath.dropFirst("/__api__/".count)
            destination.percentEncodedQuery = incoming.percentEncodedQuery
            guard let url = destination.url else { throw URLError(.badURL) }
            var request = task.request
            request.url = url
            request.mainDocumentURL = origin
            let credentials = request.value(forHTTPHeaderField: "X-Acode-Credentials") == "include"
            let redirects = request.value(forHTTPHeaderField: "X-Acode-Redirect") ?? "follow"
            request.httpShouldHandleCookies = credentials
            for key in (request.allHTTPHeaderFields ?? [:]).keys where ["host", "origin", "referer", "cookie", "content-length"].contains(key.lowercased()) || key.lowercased().hasPrefix("x-acode-") || key.lowercased().hasPrefix("sec-") {
                request.setValue(nil, forHTTPHeaderField: key)
            }
            request.setValue("https://localhost", forHTTPHeaderField: "Origin")
            request.setValue("https://localhost/", forHTTPHeaderField: "Referer")
            if credentials, AppAuthentication.accepts(url) {
                let token = try auth.secrets.get("auth_token")
                if !token.isEmpty {
                    var cookies = (HTTPCookieStorage.shared.cookies(for: url) ?? []).filter { $0.name != "token" }
                    if let cookie = HTTPCookie(properties: [.name: "token", .value: token, .domain: url.host!, .path: "/", .secure: "TRUE"]) { cookies.append(cookie) }
                    request.setValue(HTTPCookie.requestHeaderFields(with: cookies)["Cookie"], forHTTPHeaderField: "Cookie")
                }
            }
            let transport = HTTPTransport(request: request, followRedirects: redirects == "follow", connectTimeout: 30, readTimeout: 60, cookieStorage: .shared)
            transport.allowRedirect = { [origin] next in next.scheme == origin.scheme && next.host == origin.host && next.port == origin.port && next.user == nil && next.password == nil }
            transport.onResponse = { response in
                if redirects == "error", (300..<400).contains(response.statusCode) { throw URLError(.redirectToNonExistentLocation) }
                var headers: [String: String] = [:]
                for (key, value) in response.allHeaderFields {
                    let name = String(describing: key)
                    if !["set-cookie", "set-cookie2", "content-encoding", "content-length"].contains(name.lowercased()) { headers[name] = String(describing: value) }
                }
                headers["X-Acode-Response-URL"] = response.url?.absoluteString ?? url.absoluteString
                // URLSession delivers decompressed bytes, so the original encoding and size no longer apply.
                let result = HTTPURLResponse(url: source, statusCode: response.statusCode, httpVersion: "HTTP/1.1", headerFields: headers)!
                DispatchQueue.main.async { [weak self] in if self?.requests[id] != nil { task.didReceive(result) } }
            }
            transport.onData = { data in DispatchQueue.main.async { [weak self] in if self?.requests[id] != nil { task.didReceive(data) } } }
            transport.onComplete = { error in
                DispatchQueue.main.async { [weak self] in
                    guard self?.requests.removeValue(forKey: id) != nil else { return }
                    if let error { task.didFailWithError(error) }
                    else { task.didFinish() }
                }
            }
            requests[id] = transport
            transport.start()
        } catch { task.didFailWithError(error) }
    }

    func stop(_ task: WKURLSchemeTask) { requests.removeValue(forKey: ObjectIdentifier(task))?.cancel() }
}
