import Foundation
public struct PlaylistResult { public let items: [MediaItem]; public let categories: [Category]; public let guideURL: URL? }
public enum M3UParser {
    static func attributes(_ line: String) -> [String: String] {
        guard let regex = try? NSRegularExpression(pattern: "([\\w-]+)=\"([^\"]*)\"") else { return [:] }
        let ns = line as NSString
        return Dictionary(regex.matches(in: line, range: NSRange(location: 0, length: ns.length)).map { (ns.substring(with: $0.range(at: 1)), ns.substring(with: $0.range(at: 2))) }, uniquingKeysWith: { _, new in new })
    }
    static func title(_ line: String) -> String {
        var quoted = false
        for index in line.indices {
            if line[index] == "\"" { quoted.toggle() }
            if line[index] == "," && !quoted { return String(line[line.index(after: index)...]).trimmingCharacters(in: .whitespaces) }
        }
        return "Channel"
    }
    public static func parse(_ text: String, baseURL: URL) throws -> PlaylistResult {
        guard text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("#EXTM3U") else { throw ProviderError.invalidResponse }
        var out: [MediaItem] = []; var groups: [String] = []; var guideURL: URL?
        var pending: (String, [String: String])?
        for raw in text.split(whereSeparator: \.isNewline) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.hasPrefix("#EXTM3U") {
                let attrs = attributes(line)
                if let link = attrs["url-tvg"] ?? attrs["x-tvg-url"], let first = link.split(separator: ",").first,
                   let url = URL(string: String(first), relativeTo: baseURL)?.absoluteURL { guideURL = try? ProviderURL.validated(url.absoluteString) }
            } else if line.hasPrefix("#EXTINF") { pending = (title(line), attributes(line)) }
            else if !line.hasPrefix("#"), let (name, attrs) = pending {
                pending = nil
                guard let relative = URL(string: line, relativeTo: baseURL)?.absoluteURL,
                      let url = try? ProviderURL.validated(relative.absoluteString) else { continue }
                let ext = url.pathExtension.lowercased()
                let kind: MediaKind = ["mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv"].contains(ext) ? .movie : .live
                let group = attrs["group-title"].flatMap { $0.isEmpty ? nil : $0 } ?? "Other"
                if !groups.contains(group) { groups.append(group) }
                out.append(MediaItem(id: "m3u_\(out.count + 1)", name: name.isEmpty ? "Channel" : name, kind: kind, categoryID: group,
                                     artwork: attrs["tvg-logo"].flatMap { try? ProviderURL.validated($0) }, streamURL: url, epgID: attrs["tvg-id"]))
            }
        }
        guard !out.isEmpty else { throw ProviderError.invalidResponse }
        return PlaylistResult(items: out, categories: groups.map { Category(id: $0, name: $0) }, guideURL: guideURL)
    }
}
