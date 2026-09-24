import CryptoKit
import Foundation
import WebKit

struct AppAuthentication {
    static let origin = "https://acode.app"
    let secrets: SecretStore

    init(namespace: String = "acode_auth_secure") { secrets = SecretStore(namespace: namespace) }

    static func accepts(_ url: URL) -> Bool {
        url.scheme == "https" && url.host == "acode.app" && (url.port == nil || url.port == 443) && url.user == nil && url.password == nil
    }

    func begin(baseURL: String, version: Int) throws -> URL {
        guard let base = URL(string: baseURL), Self.accepts(base), ["", "/"].contains(base.path), base.query == nil, base.fragment == nil else {
            throw SecretFailure("Unsupported sign-in URL")
        }
        let state = try SecureToken.generate(bytes: 24)
        let verifier = try SecureToken.generate()
        let challenge = SHA256.hash(data: Data(verifier.utf8)).map { String(format: "%02x", $0) }.joined()
        let pending = try JSONSerialization.data(withJSONObject: ["state": state, "verifier": verifier])
        try secrets.set("pending_login", value: String(decoding: pending, as: UTF8.self))
        var url = URLComponents(string: Self.origin + "/login")!
        url.queryItems = ["redirect": "app", "authFlow": "app-code", "state": state, "challenge": challenge, "appVersionCode": String(version)].map { URLQueryItem(name: $0.key, value: $0.value) }
        return url.url!
    }

    func consume(_ url: URL) throws -> URLRequest {
        guard url.scheme == "acode", url.host == "auth", url.path == "/callback", url.user == nil, url.port == nil,
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              items.filter({ $0.name == "code" }).count == 1, items.filter({ $0.name == "state" }).count == 1,
              let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty,
              let state = items.first(where: { $0.name == "state" })?.value, !state.isEmpty,
              let pending = try JSONSerialization.jsonObject(with: Data(secrets.get("pending_login").utf8)) as? [String: String],
              SecureToken.matches(state, pending["state"]), let verifier = pending["verifier"], !verifier.isEmpty else {
            throw SecretFailure("Invalid sign-in callback")
        }
        try cancel()
        var request = URLRequest(url: URL(string: Self.origin + "/api/user/app-token/exchange")!)
        request.httpMethod = "POST"
        request.httpShouldHandleCookies = false
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["code": code, "state": state, "verifier": verifier])
        return request
    }

    func save(_ token: String) throws {
        guard !token.isEmpty, token.utf8.allSatisfy({ $0 == 0x21 || (0x23...0x2b).contains($0) || (0x2d...0x3a).contains($0) || (0x3c...0x5b).contains($0) || (0x5d...0x7e).contains($0) }) else {
            throw SecretFailure("Invalid sign-in token")
        }
        try secrets.set("auth_token", value: token)
    }

    func cancel() throws { try secrets.remove("pending_login") }
    func logout() throws { try cancel(); try secrets.remove("auth_token") }

    @MainActor
    func synchronizeCookies(_ store: WKHTTPCookieStore) async throws {
        for cookie in HTTPCookieStorage.shared.cookies ?? [] where cookie.name == "token" && ["acode.app", ".acode.app", "dev.acode.app"].contains(cookie.domain) {
            HTTPCookieStorage.shared.deleteCookie(cookie)
        }
        for cookie in await store.allCookies() where cookie.name == "token" && ["acode.app", ".acode.app", "dev.acode.app"].contains(cookie.domain) {
            await store.deleteCookie(cookie)
        }
        let token = try secrets.get("auth_token")
        if !token.isEmpty, let cookie = HTTPCookie(properties: [.name: "token", .value: token, .domain: ".acode.app", .path: "/", .secure: "TRUE", .expires: Date.distantFuture, HTTPCookiePropertyKey("HttpOnly"): "TRUE", HTTPCookiePropertyKey("SameSite"): "None"]) {
            await store.setCookie(cookie)
            HTTPCookieStorage.shared.setCookie(cookie)
        }
    }
}
