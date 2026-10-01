import Foundation
public struct ProviderClient: Sendable {
    public let credentials: ProviderCredentials
    public typealias Transport = @Sendable (URL) async throws -> Data
    private let transport: Transport
    public init(credentials: ProviderCredentials, transport: @escaping Transport = ProviderClient.fetch) {
        self.credentials = credentials; self.transport = transport
    }
    public static func fetch(_ url: URL) async throws -> Data {
        _ = try ProviderURL.validated(url.absoluteString)
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30; config.timeoutIntervalForResource = 120
        config.httpAdditionalHeaders = ["User-Agent": "RYZOD-Apple/0.1"]
        let session = URLSession(configuration: config); defer { session.invalidateAndCancel() }
        let (bytes, response) = try await session.bytes(from: url)
        guard let http = response as? HTTPURLResponse else { throw ProviderError.invalidResponse }
        guard (200...299).contains(http.statusCode) else { throw ProviderError.http(http.statusCode) }
        let limit = 64 * 1024 * 1024
        guard response.expectedContentLength <= Int64(limit) else { throw ProviderError.responseTooLarge }
        var out = Data(); var buffer = [UInt8](); buffer.reserveCapacity(16384)
        for try await byte in bytes {
            buffer.append(byte)
            if buffer.count == 16384 {
                try Task.checkCancellation()
                guard out.count + buffer.count <= limit else { throw ProviderError.responseTooLarge }
                out.append(contentsOf: buffer); buffer.removeAll(keepingCapacity: true)
            }
        }
        try Task.checkCancellation()
        guard out.count + buffer.count <= limit else { throw ProviderError.responseTooLarge }
        out.append(contentsOf: buffer); return out
    }
    private func request(_ action: String?, parameters: [String: String] = [:]) async throws -> Data {
        try Task.checkCancellation()
        let data = try await transport(credentials.api(action: action, parameters: parameters))
        try Task.checkCancellation(); return data
    }
    public func authenticate() async throws { try ProviderParser.authenticate(await request(nil)) }
    public func categories(_ kind: MediaKind) async throws -> [Category] {
        let action = kind == .live ? "get_live_categories" : kind == .movie ? "get_vod_categories" : "get_series_categories"
        return try ProviderParser.categories(await request(action))
    }
    public func media(_ kind: MediaKind) async throws -> [MediaItem] {
        let action = kind == .live ? "get_live_streams" : kind == .movie ? "get_vod_streams" : "get_series"
        return try ProviderParser.media(await request(action), kind: kind, credentials: credentials)
    }
    public func epg(channelID: String) async throws -> [Program] {
        let first = try ProviderParser.epg(await request("get_short_epg", parameters: ["stream_id": channelID, "limit": "48"]))
        if !first.isEmpty { return first }
        try Task.checkCancellation()
        return try ProviderParser.epg(await request("get_simple_data_table", parameters: ["stream_id": channelID]))
    }
    public func episodes(seriesID: String) async throws -> [Episode] {
        try ProviderParser.episodes(await request("get_series_info", parameters: ["series_id": seriesID]), credentials: credentials)
    }
}
