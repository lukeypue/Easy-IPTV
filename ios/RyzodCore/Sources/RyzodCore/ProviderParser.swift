import Foundation
public enum ProviderParser {
    public static func authenticate(_ data: Data) throws { throw ProviderError.rejectedLogin }
    public static func categories(_ data: Data) throws -> [Category] { [] }
    public static func media(_ data: Data, kind: MediaKind, credentials: ProviderCredentials) throws -> [MediaItem] { [] }
    public static func epg(_ data: Data) throws -> [Program] { [] }
    public static func episodes(_ data: Data, credentials: ProviderCredentials) throws -> [Episode] { [] }
}
public struct PlaylistResult { public let items: [MediaItem]; public let categories: [Category]; public let guideURL: URL? }
public enum M3UParser { public static func parse(_ text: String, baseURL: URL) throws -> PlaylistResult { PlaylistResult(items: [], categories: [], guideURL: nil) } }
