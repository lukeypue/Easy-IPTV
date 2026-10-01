import Foundation
public enum MediaKind: String, Codable, CaseIterable, Sendable { case live, movie, series }
public struct MediaItem: Identifiable, Hashable, Codable, Sendable {
    public let id: String
    public let name: String
    public let kind: MediaKind
    public let categoryID: String?
    public let artwork: URL?
    public let streamURL: URL?
    public let epgID: String?
    public let searchText: String
    public init(id: String, name: String, kind: MediaKind, categoryID: String? = nil, artwork: URL? = nil, streamURL: URL? = nil, epgID: String? = nil, searchText: String = "") {
        self.id = id; self.name = name; self.kind = kind; self.categoryID = categoryID
        self.artwork = artwork; self.streamURL = streamURL; self.epgID = epgID; self.searchText = searchText
    }
    public var favoriteKey: String { "\(kind.rawValue):\(id)" }
}
public struct Category: Identifiable, Hashable, Sendable {
    public let id: String; public let name: String
    public init(id: String, name: String) { self.id = id; self.name = name }
}
public struct Program: Identifiable, Hashable, Sendable {
    public let title: String; public let detail: String; public let start: Date; public let end: Date
    public var id: String { "\(start.timeIntervalSince1970):\(end.timeIntervalSince1970):\(title)" }
    public init(title: String, detail: String = "", start: Date, end: Date) {
        self.title = title; self.detail = detail; self.start = start; self.end = end
    }
}
public struct Episode: Identifiable, Hashable, Sendable {
    public let id: String; public let title: String; public let season: Int; public let number: Int; public let url: URL
    public init(id: String, title: String, season: Int, number: Int, url: URL) {
        self.id = id; self.title = title; self.season = season; self.number = number; self.url = url
    }
}
public enum ProviderError: Error, LocalizedError {
    case invalidServer, missingCredentials, rejectedLogin, invalidResponse, unsupportedURL, responseTooLarge, http(Int)
    public var errorDescription: String? {
        switch self {
        case .invalidServer: return "Enter a valid provider address, including its port if needed."
        case .missingCredentials: return "Enter your provider username and password."
        case .rejectedLogin: return "Your provider rejected this login. Check your credentials and subscription."
        case .invalidResponse: return "The provider returned an unexpected response. Please try again."
        case .unsupportedURL: return "Use an HTTP or HTTPS address."
        case .responseTooLarge: return "The provider response is too large for this device."
        case .http(let status): return "The provider request failed (HTTP \(status))."
        }
    }
}
