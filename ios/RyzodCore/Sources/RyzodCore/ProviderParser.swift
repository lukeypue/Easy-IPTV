import Foundation
public enum ProviderParser {
    static func text(_ value: Any?) -> String {
        if let s = value as? String { return s }
        if let n = value as? NSNumber { return n.stringValue }
        return ""
    }
    static func object(_ data: Data) throws -> Any {
        do { return try JSONSerialization.jsonObject(with: data) } catch { throw ProviderError.invalidResponse }
    }
    static func rows(_ data: Data, wrappers: [String]) throws -> [[String: Any]] {
        let root = try object(data)
        if let array = root as? [Any] { return array.compactMap { $0 as? [String: Any] } }
        if let root = root as? [String: Any] {
            for key in wrappers {
                if let array = root[key] as? [Any] { return array.compactMap { $0 as? [String: Any] } }
            }
        }
        throw ProviderError.invalidResponse
    }
    public static func authenticate(_ data: Data) throws {
        guard let root = try object(data) as? [String: Any], let user = root["user_info"] as? [String: Any], text(user["auth"]) == "1" else { throw ProviderError.rejectedLogin }
        let status = text(user["status"]).lowercased()
        if !status.isEmpty && status != "active" { throw ProviderError.rejectedLogin }
    }
    public static func categories(_ data: Data) throws -> [Category] {
        var seen = Set<String>()
        return try rows(data, wrappers: ["data", "results", "categories"]).compactMap { row in
            let id = text(row["category_id"])
            guard !id.isEmpty, seen.insert(id).inserted else { return nil }
            let name = text(row["category_name"])
            return Category(id: id, name: name.isEmpty ? "Other" : name)
        }
    }
    public static func media(_ data: Data, kind: MediaKind, credentials: ProviderCredentials) throws -> [MediaItem] {
        let wrappers = kind == .series ? ["data", "results", "series"] : ["data", "results", "streams", "movies", "vod"]
        var seen = Set<String>()
        return try rows(data, wrappers: wrappers).compactMap { row in
            let id = text(kind == .series ? (row["series_id"] ?? row["id"]) : row["stream_id"])
            guard !id.isEmpty, seen.insert(id).inserted else { return nil }
            let name = text(row["name"])
            let ext = kind == .live ? "m3u8" : text(row["container_extension"])
            let stream = kind == .series ? nil : try credentials.stream(kind: kind, id: id, fileExtension: ext)
            let icon = text(row["stream_icon"] ?? row["cover"] ?? row["movie_image"])
            let category = text(row["category_id"]), epg = text(row["epg_channel_id"])
            let metadata = ["plot", "cast", "director", "genre", "releaseDate", "releasedate"].map { text(row[$0]) }.joined(separator: " ")
            return MediaItem(id: id, name: name.isEmpty ? kind.rawValue.capitalized : name, kind: kind,
                             categoryID: category.isEmpty ? nil : category, artwork: try? ProviderURL.validated(icon),
                             streamURL: stream, epgID: epg.isEmpty ? nil : epg, searchText: metadata)
        }
    }
    static func decoded(_ value: Any?) -> String {
        let original = text(value)
        guard !original.isEmpty, let bytes = Data(base64Encoded: original), let string = String(data: bytes, encoding: .utf8),
              !string.isEmpty, !string.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) && !"\n\r\t".unicodeScalars.contains($0) }) else { return original }
        return string
    }
    static func timestamp(_ row: [String: Any], unix: String, alternatives: [String]) -> Date? {
        if let seconds = Double(text(row[unix])), seconds.isFinite, seconds > 0 {
            return Date(timeIntervalSince1970: seconds > 100_000_000_000 ? seconds / 1000 : seconds)
        }
        for key in alternatives {
            let value = text(row[key])
            if let parsed = ISO8601DateFormatter().date(from: value) { return parsed }
            for format in ["yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss"] {
                let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX"); formatter.dateFormat = format
                if let date = formatter.date(from: value) { return date }
            }
        }
        return nil
    }
    public static func epg(_ data: Data) throws -> [Program] {
        let rows = try rows(data, wrappers: ["epg_listings", "data", "results"])
        return rows.compactMap { row in
            guard let start = timestamp(row, unix: "start_timestamp", alternatives: ["start"]),
                  let end = timestamp(row, unix: "stop_timestamp", alternatives: ["end", "stop"]), end > start else { return nil }
            return Program(title: decoded(row["title"]), detail: decoded(row["description"]), start: start, end: end)
        }.sorted { $0.start < $1.start }
    }
    public static func episodes(_ data: Data, credentials: ProviderCredentials) throws -> [Episode] {
        guard let root = try object(data) as? [String: Any] else { throw ProviderError.invalidResponse }
        var groups: [(Int, [[String: Any]])] = []
        if let grouped = root["episodes"] as? [String: Any] {
            groups = grouped.map { (Int($0.key) ?? 0, ($0.value as? [Any] ?? []).compactMap { $0 as? [String: Any] }) }
        } else if let array = root["episodes"] as? [Any] { groups = [(0, array.compactMap { $0 as? [String: Any] })] }
        else { throw ProviderError.invalidResponse }
        var out: [Episode] = []; var seen = Set<String>()
        for (fallbackSeason, rows) in groups {
            for (offset, row) in rows.enumerated() {
                let id = text(row["id"] ?? row["episode_id"] ?? row["stream_id"])
                guard !id.isEmpty, seen.insert(id).inserted else { continue }
                let info = row["info"] as? [String: Any] ?? [:]
                let season = Int(text(row["season"] ?? info["season"])) ?? fallbackSeason
                let number = Int(text(row["episode_num"] ?? info["episode_num"])) ?? offset + 1
                let title = text(row["title"] ?? info["title"])
                let url = try credentials.stream(kind: .series, id: id, fileExtension: text(row["container_extension"] ?? info["container_extension"]))
                out.append(Episode(id: id, title: title.isEmpty ? "Episode \(number)" : title, season: season, number: number, url: url))
            }
        }
        return out.sorted { $0.season == $1.season ? $0.number < $1.number : $0.season < $1.season }
    }
}
