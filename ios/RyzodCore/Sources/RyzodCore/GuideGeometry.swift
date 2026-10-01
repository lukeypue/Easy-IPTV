import Foundation
public struct GuideCell: Identifiable, Sendable {
    public let start: Date; public let end: Date; public let program: Program?
    public var id: Double { start.timeIntervalSince1970 }
    public var duration: TimeInterval { end.timeIntervalSince(start) }
}
public enum GuideGeometry { public static func cells(programs: [Program], start: Date, end: Date) -> [GuideCell] { [] } }
