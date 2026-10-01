import Foundation
public struct ProviderCredentials: Codable, Hashable, Sendable {
    public let server: String; public let username: String; public let password: String
    public init(server: String, username: String, password: String) { self.server = server; self.username = username; self.password = password }
    private func base() throws -> URLComponents {
        var address = server.trimmingCharacters(in: .whitespacesAndNewlines)
        if !address.contains("://") { address = "http://" + address }
        guard var parts = URLComponents(string: address), ["http", "https"].contains(parts.scheme?.lowercased() ?? ""),
              let host = parts.host, !host.isEmpty, parts.user == nil, parts.password == nil else { throw ProviderError.invalidServer }
        guard !username.isEmpty, !password.isEmpty else { throw ProviderError.missingCredentials }
        parts.query = nil; parts.fragment = nil
        while parts.percentEncodedPath.hasSuffix("/") { parts.percentEncodedPath.removeLast() }
        return parts
    }
    public func api(action: String? = nil, parameters: [String: String] = [:]) throws -> URL {
        var parts = try base()
        parts.percentEncodedPath += "/player_api.php"
        var query = [URLQueryItem(name: "username", value: username), URLQueryItem(name: "password", value: password)]
        if let action { query.append(URLQueryItem(name: "action", value: action)) }
        query += parameters.keys.sorted().filter { !["username", "password", "action"].contains($0) }.map { URLQueryItem(name: $0, value: parameters[$0]) }
        parts.queryItems = query
        // A literal plus is treated as a space by many provider panels.
        parts.percentEncodedQuery = parts.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        guard let url = parts.url else { throw ProviderError.invalidServer }; return url
    }
    public func stream(kind: MediaKind, id: String, fileExtension: String) throws -> URL {
        var parts = try base()
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
        func segment(_ value: String) throws -> String {
            guard !value.isEmpty, let encoded = value.addingPercentEncoding(withAllowedCharacters: allowed) else { throw ProviderError.invalidResponse }
            return encoded
        }
        let ext = fileExtension.isEmpty ? (kind == .live ? "m3u8" : "mp4") : fileExtension
        guard ext.unicodeScalars.allSatisfy({ CharacterSet.alphanumerics.contains($0) }) else { throw ProviderError.invalidResponse }
        parts.percentEncodedPath += "/\(kind.rawValue)/\(try segment(username))/\(try segment(password))/\(try segment(id)).\(ext)"
        guard let url = parts.url else { throw ProviderError.invalidServer }; return url
    }
}
public enum ProviderURL {
    public static func validated(_ value: String) throws -> URL {
        guard let url = URL(string: value.trimmingCharacters(in: .whitespacesAndNewlines)),
              ["http", "https"].contains(url.scheme?.lowercased() ?? ""), let host = url.host, !host.isEmpty else { throw ProviderError.unsupportedURL }
        return url
    }
}
