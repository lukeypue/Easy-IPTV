import XCTest
final class LoginTests: XCTestCase {
    func testFirstLaunchShowsBrandAndRequiresProviderDetails() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        XCTAssertTrue(app.staticTexts["Your media. Your player."].waitForExistence(timeout: 10))
        let connect = app.buttons["Connect"]
        XCTAssertTrue(connect.exists); XCTAssertFalse(connect.isEnabled)
        let server = app.textFields["Server address"]
        server.tap(); server.typeText("https://example.test")
        let user = app.textFields["Username"]
        user.tap(); user.typeText("testuser")
        let password = app.secureTextFields["Password"]
        password.tap(); password.typeText("password")
        XCTAssertTrue(connect.isEnabled)
    }
    func testPlaylistModeShowsURLAndKeepsConnectDisabledWhenEmpty() {
        let app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
        app.buttons["Playlist link"].tap()
        XCTAssertTrue(app.textFields["Playlist URL"].exists)
        XCTAssertFalse(app.buttons["Connect"].isEnabled)
    }
}
