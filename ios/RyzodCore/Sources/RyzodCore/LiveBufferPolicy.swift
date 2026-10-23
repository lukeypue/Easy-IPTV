import Foundation

public enum LiveBufferChoice: Int, CaseIterable, Sendable {
    case realTime = 0, small = 10, twenty = 20, normal = 30, big = 60
    public init(savedSeconds: Int) { self = Self(rawValue: savedSeconds) ?? .normal }
    // Apple's zero preference means automatic. Real time removes deliberate
    // waiting separately and retains a small forward decoder queue.
    public var preferredForwardDuration: TimeInterval { self == .realTime ? 5 : Double(rawValue) }
    public var startsImmediately: Bool { self == .realTime }
    public var label: String { self == .realTime ? "Real time (0s)" : "\(rawValue)s refill goal" }
}

public struct BufferedInterval: Equatable, Sendable {
    public let start: Double
    public let duration: Double
    public init(start: Double, duration: Double) { self.start = start; self.duration = duration }
    fileprivate var end: Double { start + duration }
    fileprivate var valid: Bool { start.isFinite && duration.isFinite && duration > 0 && end.isFinite }
}

public enum PlaybackBufferHealth {
    /// Only playable, contiguous data ahead counts. Future ranges beyond a gap
    /// must not inflate the reserve displayed to the viewer.
    public static func ahead(position: Double, ranges: [BufferedInterval]) -> Double {
        guard position.isFinite, position >= 0 else { return 0 }
        var end = position
        for range in ranges.filter(\.valid).sorted(by: { $0.start < $1.start }) {
            if range.end <= end { continue }
            if range.start > end + 0.05 { break }
            end = range.end
        }
        return max(0, end - position)
    }
    /// Seekable range end is an available media edge, not a wall-clock claim.
    public static func behindAvailableEdge(position: Double, ranges: [BufferedInterval]) -> Double? {
        guard position.isFinite, position >= 0,
              let end = ranges.filter(\.valid).map(\.end).max(), end >= position else { return nil }
        return end - position
    }
}
