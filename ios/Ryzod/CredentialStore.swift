import Foundation
import Security
import RyzodCore
struct ProviderProfile: Codable, Hashable, Sendable {
    enum Kind: String, Codable { case xtream, m3u }
    var kind: Kind = .xtream
    var server = ""; var username = ""; var password = ""; var playlistURL = ""
    var credentials: ProviderCredentials { ProviderCredentials(server: server, username: username, password: password) }
}
enum CredentialStore {
    private static let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "com.ryzod.player", kSecAttrAccount as String: "provider-profile"]
    static func load() throws -> ProviderProfile? {
        var read = query; read[kSecReturnData as String] = true; read[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(read as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw StoreError.unavailable }
        return try JSONDecoder().decode(ProviderProfile.self, from: data)
    }
    static func save(_ profile: ProviderProfile) throws {
        let data = try JSONEncoder().encode(profile)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var add = query; add[kSecValueData as String] = data; add[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            guard SecItemAdd(add as CFDictionary, nil) == errSecSuccess else { throw StoreError.unavailable }
        } else if status != errSecSuccess { throw StoreError.unavailable }
    }
    static func clear() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw StoreError.unavailable }
    }
    enum StoreError: Error, LocalizedError {
        case unavailable
        var errorDescription: String? { "The iPhone could not access secure credential storage. Unlock it and try again." }
    }
}
