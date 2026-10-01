import Foundation
public struct GuideCell: Identifiable, Sendable {
    public let start: Date; public let end: Date; public let program: Program?
    public var id: Double { start.timeIntervalSince1970 }
    public var duration: TimeInterval { end.timeIntervalSince(start) }
}
public enum GuideGeometry {
    public static func cells(programs: [Program], start: Date, end: Date) -> [GuideCell] {
        guard end > start else { return [] }
        var cells: [GuideCell] = []; var cursor = start
        for program in programs.sorted(by: { $0.start < $1.start }) {
            guard program.end > cursor, program.start < end, program.end > program.start else { continue }
            let left = max(cursor, program.start)
            if left > cursor { cells.append(GuideCell(start: cursor, end: left, program: nil)) }
            let right = min(end, program.end)
            if right > left { cells.append(GuideCell(start: left, end: right, program: program)); cursor = right }
            if cursor >= end { break }
        }
        if cursor < end { cells.append(GuideCell(start: cursor, end: end, program: nil)) }
        return cells
    }
}
