import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif
public enum XMLTVParser {
    public static func parse(_ data: Data, from: Date? = nil, until: Date? = nil) throws -> [String: [Program]] {
        let delegate = GuideXMLDelegate(from: from, until: until)
        let parser = XMLParser(data: data); parser.shouldResolveExternalEntities = false; parser.delegate = delegate
        guard parser.parse() else { throw ProviderError.invalidResponse }
        return delegate.results.mapValues { $0.sorted { $0.start < $1.start } }
    }
}
private final class GuideXMLDelegate: NSObject, XMLParserDelegate {
    var results: [String: [Program]] = [:]
    let from: Date?; let until: Date?
    var channel = ""; var start: Date?; var end: Date?; var title = ""; var detail = ""; var current = ""
    let formatter: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyyMMddHHmmss Z"; return f
    }()
    init(from: Date?, until: Date?) { self.from = from; self.until = until }
    func date(_ value: String?) -> Date? {
        guard let value else { return nil }
        if let d = formatter.date(from: value) { return d }
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(secondsFromGMT: 0); f.dateFormat = "yyyyMMddHHmmss"
        return f.date(from: value)
    }
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes: [String: String]) {
        if elementName == "programme" {
            channel = attributes["channel"] ?? ""; start = date(attributes["start"]); end = date(attributes["stop"]); title = ""; detail = ""
        }
        current = elementName
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if current == "title" { title += string }
        if current == "desc" { detail += string }
    }
    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        if elementName == "programme", !channel.isEmpty, let start, let end, end > start,
           from.map({ end > $0 }) ?? true, until.map({ start < $0 }) ?? true {
            results[channel, default: []].append(Program(title: title.trimmingCharacters(in: .whitespacesAndNewlines), detail: detail.trimmingCharacters(in: .whitespacesAndNewlines), start: start, end: end))
        }
        current = ""
    }
}
