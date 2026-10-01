import XCTest
@testable import RyzodCore
final class CoreTests: XCTestCase {
    let credentials = ProviderCredentials(server: "https://example.test:8443/", username: "a/b+ c", password: "p%?#&é")
    func data(_ string: String) -> Data { Data(string.utf8) }
    func date(_ seconds: Double) -> Date { Date(timeIntervalSince1970: seconds) }
    func testAPIEncodesSpecialCredentialsAndKeepsPort() throws {
        let url = try credentials.api(action: "get_short_epg", parameters: ["stream_id": "12"])
        let parts = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertEqual(parts.host, "example.test"); XCTAssertEqual(parts.port, 8443)
        XCTAssertEqual(parts.path, "/player_api.php")
        let query = Dictionary(uniqueKeysWithValues: (parts.queryItems ?? []).map { ($0.name, $0.value ?? "") })
        XCTAssertEqual(query["username"], "a/b+ c"); XCTAssertEqual(query["password"], "p%?#&é")
        XCTAssertEqual(query["stream_id"], "12"); XCTAssertFalse(url.absoluteString.contains("username=a/b+"))
    }
    func testStreamEncodesSlashInsideCredentialAsOneSegment() throws {
        let url = try credentials.stream(kind: .live, id: "42", fileExtension: "m3u8")
        XCTAssertEqual(url.absoluteString, "https://example.test:8443/live/a%2Fb%2B%20c/p%25%3F%23%26%C3%A9/42.m3u8")
    }
    func testInvalidSchemesAndBlankCredentialsAreRejected() {
        XCTAssertThrowsError(try ProviderCredentials(server: "file:///tmp/a", username: "u", password: "p").api())
        XCTAssertThrowsError(try ProviderCredentials(server: "https://example.test", username: "", password: "p").api())
    }
    func testNumericAndStringAuthentication() throws {
        try ProviderParser.authenticate(data(#"{"user_info":{"auth":1,"status":"Active"}}"#))
        try ProviderParser.authenticate(data(#"{"user_info":{"auth":"1"}}"#))
        XCTAssertThrowsError(try ProviderParser.authenticate(data(#"{"user_info":{"auth":0}}"#)))
    }
    func testMixedCategoryIDs() throws {
        let items = try ProviderParser.categories(data(#"[{"category_id":7,"category_name":"News"},{"category_id":"8","category_name":"Sports"}]"#))
        XCTAssertEqual(items.map(\.id), ["7", "8"])
    }
    func testWrappedMoviesAndDeduplication() throws {
        let items = try ProviderParser.media(data(#"{"movies":[{"stream_id":7,"name":"20/20","container_extension":"mp4","category_id":"3"},{"stream_id":"7","name":"Duplicate"}]}"#), kind: .movie, credentials: credentials)
        XCTAssertEqual(items.count, 1); XCTAssertEqual(items.first?.name, "20/20")
        XCTAssertTrue(items.first?.streamURL?.absoluteString.hasSuffix("/7.mp4") == true)
    }
    func testLoginErrorIsNotAnEmptyCatalog() {
        XCTAssertThrowsError(try ProviderParser.media(data(#"{"user_info":{"auth":0},"message":"Unauthorized"}"#), kind: .movie, credentials: credentials))
        XCTAssertThrowsError(try ProviderParser.categories(data("<html>Login required</html>")))
    }
    func testLiveDefaultsToAppleHLSRatherThanRawTS() throws {
        let items = try ProviderParser.media(data(#"[{"stream_id":5,"name":"News","epg_channel_id":"news.example"}]"#), kind: .live, credentials: credentials)
        XCTAssertTrue(items.first?.streamURL?.absoluteString.hasSuffix("/5.m3u8") == true)
        XCTAssertEqual(items.first?.epgID, "news.example")
    }
    func testEPGBase64AndMillisecondsAndInvalidRanges() throws {
        let programs = try ProviderParser.epg(data(#"{"epg_listings":[{"title":"TmV3cw==","description":"V29ybGQ=","start_timestamp":"1700000000000","stop_timestamp":1700003600000},{"title":"bad","start_timestamp":5,"stop_timestamp":4}]}"#))
        XCTAssertEqual(programs.count, 1); XCTAssertEqual(programs.first?.title, "News")
        XCTAssertEqual(programs.first?.detail, "World"); XCTAssertEqual(programs.first?.start, date(1700000000))
    }
    func testXMLTVTimezoneAndChannelMapping() throws {
        let xml = #"<tv><programme channel="news" start="20260930200000 -0600" stop="20260930220000 -0600"><title>Two hour show</title><desc>Details</desc></programme></tv>"#
        let result = try XMLTVParser.parse(data(xml))
        XCTAssertEqual(result["news"]?.first?.start, date(1790820000))
        XCTAssertEqual(result["news"]?.first?.end.timeIntervalSince(result["news"]!.first!.start), 7200)
    }
    func testGuideKeepsTwoHourShowAsOneCell() {
        let p = Program(title: "Long show", start: date(0), end: date(7200))
        let cells = GuideGeometry.cells(programs: [p], start: date(0), end: date(7200))
        XCTAssertEqual(cells.count, 1); XCTAssertEqual(cells.first?.duration, 7200)
    }
    func testGuideGapsOverlapAndClippingCoverWindow() {
        let programs = [Program(title: "A", start: date(-100), end: date(100)), Program(title: "B", start: date(50), end: date(200)), Program(title: "C", start: date(300), end: date(500))]
        let cells = GuideGeometry.cells(programs: programs, start: date(0), end: date(400))
        XCTAssertEqual(cells.map(\.duration), [100, 100, 100, 100]); if cells.count > 2 { XCTAssertNil(cells[2].program) }
        XCTAssertEqual(cells.map { $0.start.timeIntervalSince1970 }, [0, 100, 200, 300])
    }
    func testM3UAttributesRelativeURLAndCommasInTitle() throws {
        let text = "#EXTM3U x-tvg-url=\"https://guide.test/epg.xml\"\n#EXTINF:-1 tvg-id=\"news\" group-title=\"Local News\",News, Local\nstreams/news.m3u8\n#EXTINF:-1,Movie\nhttps://media.test/a.mp4\n"
        let result = try M3UParser.parse(text, baseURL: URL(string: "https://provider.test/list/index.m3u")!)
        XCTAssertEqual(result.items.count, 2); XCTAssertEqual(result.items.first?.name, "News, Local")
        XCTAssertEqual(result.items.first?.streamURL?.absoluteString, "https://provider.test/list/streams/news.m3u8")
        XCTAssertEqual(result.items.first?.epgID, "news"); XCTAssertEqual(result.categories.first?.name, "Local News")
        XCTAssertEqual(result.guideURL?.host, "guide.test")
    }
    func testEpisodeOrderingAndNestedExtension() throws {
        let items = try ProviderParser.episodes(data(#"{"episodes":{"2":[{"id":12,"title":"Later","episode_num":2,"info":{"container_extension":"mkv"}}],"1":[{"id":"11","title":"First","episode_num":1,"container_extension":"mp4"}]}}"#), credentials: credentials)
        XCTAssertEqual(items.map(\.id), ["11", "12"])
        XCTAssertTrue(items.last?.url.absoluteString.hasSuffix("/12.mkv") == true)
    }
    func testFailedShortEPGFallsBackToFullTable() async throws {
        let client = ProviderClient(credentials: credentials) { url in
            let action = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "action" }?.value
            if action == "get_short_epg" { throw ProviderError.http(503) }
            guard action == "get_simple_data_table" else { throw ProviderError.invalidResponse }
            return Data(#"{"epg_listings":[{"title":"Fallback show","start_timestamp":1700000000,"stop_timestamp":1700003600}]}"#.utf8)
        }
        let programs = try await client.epg(channelID: "5")
        XCTAssertEqual(programs.first?.title, "Fallback show")
    }
    func testCancellationDoesNotRunGuideFallback() async {
        let client = ProviderClient(credentials: credentials) { url in
            let action = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "action" }?.value
            if action == "get_short_epg" { throw CancellationError() }
            return Data(#"{"epg_listings":[]}"#.utf8)
        }
        do { _ = try await client.epg(channelID: "5"); XCTFail("Cancelled request must not succeed") }
        catch is CancellationError { }
        catch { XCTFail("Cancellation must propagate") }
    }

    func testPlaylistFavoritesKeepIdentityAfterInsertAndReorder() throws {
        let a = "#EXTINF:-1 tvg-id=\"news\",News\nhttps://media.test/news.m3u8\n"
        let b = "#EXTINF:-1,Sports\nhttps://media.test/sport.m3u8\n"
        let c = "#EXTINF:-1,New\nhttps://media.test/new.m3u8\n"
        let base = URL(string: "https://media.test/list.m3u")!
        let first = try M3UParser.parse("#EXTM3U\n" + a + b, baseURL: base)
        let next = try M3UParser.parse("#EXTM3U\n" + c + b + a + a, baseURL: base)
        for old in first.items {
            XCTAssertEqual(next.items.first { $0.streamURL == old.streamURL }?.favoriteKey, old.favoriteKey)
        }
        XCTAssertEqual(next.items.count, 3, "Identical repeated streams must not duplicate favorite identity")
    }
    func testNonXMLTVResponseIsRejectedButEmptyTVIsValid() throws {
        XCTAssertThrowsError(try XMLTVParser.parse(data("<html><body>Login required</body></html>")))
        XCTAssertThrowsError(try XMLTVParser.parse(data("<response><tv/></response>")))
        XCTAssertTrue(try XMLTVParser.parse(data("<tv/>" )).isEmpty)
    }

}
