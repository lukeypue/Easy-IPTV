import XCTest
@testable import RyzodCore
final class BufferPolicyTests: XCTestCase {
    func testRealTimeRemovesAddedWaitingWithoutUsingApplesAutomaticZeroPreference() {
        XCTAssertTrue(LiveBufferChoice.realTime.startsImmediately)
        XCTAssertEqual(LiveBufferChoice.realTime.preferredForwardDuration, 5)
        XCTAssertFalse(LiveBufferChoice.twenty.startsImmediately)
        XCTAssertEqual(LiveBufferChoice.twenty.preferredForwardDuration, 20)
        XCTAssertEqual(LiveBufferChoice(savedSeconds: 999), .normal)
    }
    func testAheadStopsAtGapsAndMergesOverlappingRanges() {
        XCTAssertEqual(PlaybackBufferHealth.ahead(position: 11, ranges: [.init(start: 18, duration: 12), .init(start: 10, duration: 5)]), 4)
        XCTAssertEqual(PlaybackBufferHealth.ahead(position: 9, ranges: [.init(start: 10, duration: 20), .init(start: 0, duration: 12)]), 21)
        XCTAssertEqual(PlaybackBufferHealth.ahead(position: 5, ranges: [.init(start: 10, duration: 20)]), 0)
    }
    func testUnknownAndInvalidRangesDoNotManufactureBufferOrLiveEdge() {
        XCTAssertNil(PlaybackBufferHealth.behindAvailableEdge(position: 11, ranges: []))
        XCTAssertNil(PlaybackBufferHealth.behindAvailableEdge(position: 31, ranges: [.init(start: 10, duration: 20)]))
        XCTAssertEqual(PlaybackBufferHealth.behindAvailableEdge(position: 11, ranges: [.init(start: 10, duration: 20)]), 19)
        XCTAssertEqual(PlaybackBufferHealth.ahead(position: 0, ranges: [.init(start: .nan, duration: 100), .init(start: 0, duration: .infinity)]), 0)
        XCTAssertEqual(PlaybackBufferHealth.ahead(position: .nan, ranges: [.init(start: 0, duration: 10)]), 0)
    }
}
