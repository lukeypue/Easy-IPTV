import Foundation
public struct ProviderCredentials: Codable, Hashable, Sendable {
    public let server: String; public let username: String; public let password: String
    public init(server: String, username: String, password: String) { self.server = server; self.username = username; self.password = password }
    public func api(action: String? = nil, parameters: [String: String] = [:]) throws -> URL { throw ProviderError.invalidServer }
    public func stream(kind: MediaKind, id: String, fileExtension: String) throws -> URL { throw ProviderError.invalidServer }
}
