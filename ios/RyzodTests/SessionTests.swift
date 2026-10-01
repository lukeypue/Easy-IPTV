import XCTest
import RyzodCore
@testable import Ryzod

@MainActor final class SessionTests: XCTestCase {
    private func waitUntil(_ condition: @escaping () -> Bool) async throws {
        for _ in 0..<300 {
            if condition() { return }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTFail("Timed out waiting for model state")
    }
    func testActualHTTPTransportIsNotBlockedByApplicationATS() async {
        // Run in the app host: package tests do not load the application's ATS policy.
        var request = URLRequest(url: URL(string: "http://example.com/")!)
        request.timeoutInterval = 5
        do { _ = try await URLSession.shared.data(for: request) }
        catch {
            XCTAssertNotEqual((error as NSError).code, URLError.appTransportSecurityRequiresSecureConnection.rawValue,
                              "The app promises HTTP provider login, which must not be blocked by ATS")
        }
    }
    func testPreviousChannelCannotCrossSignOutAndAccountSwitch() {
        let playback = PlaybackController()
        func channel(_ id: String, account: String) -> MediaItem {
            MediaItem(id: id, name: id, kind: .live, streamURL: URL(fileURLWithPath: "/tmp/\(account)-\(id).m3u8"))
        }
        playback.play(channel("1", account: "A"), fullScreen: false)
        playback.play(channel("2", account: "A"), fullScreen: false)
        XCTAssertNotNil(playback.previousLive)
        playback.stop() // Settings' sign-out path releases the player before clearing the model.
        playback.play(channel("3", account: "B"), fullScreen: false)
        XCTAssertNil(playback.previousLive)
        playback.stop()
    }
    func testGuideRefreshIncludesCachedSelectedChannelBeyondFirstTwenty() async throws {
        let stub = FixtureProvider()
        let model = AppModel(transport: { try await stub.request($0) })
        await model.connect(ProviderProfile(server: "https://example.test", username: "u", password: "p"), persist: false)
        let item = try XCTUnwrap(model.items[.live]?.first { $0.id == "25" })
        model.selectedLiveID = item.id
        model.loadGuide(for: item)
        try await waitUntil { model.guide[item.id]?.first?.title == "Guide 1" }
        await stub.setVersion(2)
        model.refreshGuide()
        try await waitUntil { !model.guideLoading }
        XCTAssertEqual(model.guide[item.id]?.first?.title, "Guide 2")
    }
    func testXMLTVErrorPreservesPreviousGuide() async throws {
        let stub = FixtureProvider()
        let model = AppModel(transport: { try await stub.request($0) })
        await model.connect(ProviderProfile(kind: .m3u, playlistURL: "https://example.test/list.m3u"), persist: false)
        try await waitUntil { !model.guideLoading }
        let old = model.guide
        XCTAssertFalse(old.values.flatMap { $0 }.isEmpty)
        await stub.setBadXML()
        model.refreshGuide()
        try await waitUntil { !model.guideLoading }
        XCTAssertEqual(model.guide, old)
        XCTAssertTrue(model.guideStatus.contains("failed"))
    }
    func testLateOldAccountGuideCannotRepopulateNewAccount() async throws {
        let stub = FixtureProvider()
        let model = AppModel(transport: { try await stub.request($0) })
        await model.connect(ProviderProfile(server: "https://example.test", username: "A", password: "p"), persist: false)
        await stub.holdGuides()
        model.loadGuide(for: try XCTUnwrap(model.items[.live]?.last))
        for _ in 0..<300 {
            if await stub.pendingCount() > 0 { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        let count = await stub.pendingCount(); XCTAssertEqual(count, 1)
        model.signOut()
        await model.connect(ProviderProfile(server: "https://example.test", username: "B", password: "p"), persist: false)
        await stub.releaseGuides()
        try await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(model.profile?.username, "B")
        XCTAssertTrue(model.guide.isEmpty)
    }
    func testVisibleGuideRequestsAreLimitedToThree() async throws {
        let stub = FixtureProvider()
        let model = AppModel(transport: { try await stub.request($0) })
        await model.connect(ProviderProfile(server: "https://example.test", username: "u", password: "p"), persist: false)
        await stub.holdGuides()
        for item in model.items[.live] ?? [] { model.loadGuide(for: item) }
        for _ in 0..<300 {
            if await stub.pendingCount() >= 3 { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        try await Task.sleep(nanoseconds: 50_000_000)
        let count = await stub.pendingCount(); XCTAssertEqual(count, 3)
        await stub.releaseGuides()
        try await waitUntil { model.guide.count == 30 }
    }
}

private actor FixtureProvider {
    private var version = 1
    private var badXML = false
    private var holding = false
    private var pending: [(CheckedContinuation<Data, Error>, Data)] = []
    func setVersion(_ value: Int) { version = value }
    func setBadXML() { badXML = true }
    func holdGuides() { holding = true }
    func pendingCount() -> Int { pending.count }
    func releaseGuides() {
        holding = false
        let saved = pending; pending = []
        for (continuation, data) in saved { continuation.resume(returning: data) }
    }
    func request(_ url: URL) async throws -> Data {
        let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        let action = query.first { $0.name == "action" }?.value
        if url.path.hasSuffix("list.m3u") {
            return Data("#EXTM3U x-tvg-url=\"https://example.test/guide.xml\"\n#EXTINF:-1 tvg-id=\"news\",News\nhttps://example.test/news.m3u8\n".utf8)
        }
        if url.path.hasSuffix("guide.xml") {
            if badXML { return Data("<html><body>Login required</body></html>".utf8) }
            let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(secondsFromGMT: 0); f.dateFormat = "yyyyMMddHHmmss Z"
            return Data("<tv><programme channel=\"news\" start=\"\(f.string(from: Date()))\" stop=\"\(f.string(from: Date().addingTimeInterval(3600)))\"><title>News</title></programme></tv>".utf8)
        }
        if action == nil { return Data(#"{"user_info":{"auth":1,"status":"Active"}}"#.utf8) }
        if action == "get_live_streams" {
            return try JSONSerialization.data(withJSONObject: (1...30).map { ["stream_id": $0, "name": "Channel \($0)", "category_id": $0 > 20 ? "2" : "1"] as [String: Any] })
        }
        if action == "get_live_categories" { return Data(#"[{"category_id":1,"category_name":"First"},{"category_id":2,"category_name":"Other"}]"#.utf8) }
        if action == "get_short_epg" || action == "get_simple_data_table" {
            let data = try JSONSerialization.data(withJSONObject: ["epg_listings": [["title": "Guide \(version)", "start_timestamp": Date().timeIntervalSince1970, "stop_timestamp": Date().addingTimeInterval(3600).timeIntervalSince1970]]])
            if holding { return try await withCheckedThrowingContinuation { pending.append(($0, data)) } }
            return data
        }
        throw ProviderError.invalidResponse
    }
}
