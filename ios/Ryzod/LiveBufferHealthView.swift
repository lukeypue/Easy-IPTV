import SwiftUI
struct LiveBufferHealthView: View {
    @EnvironmentObject var playback: PlaybackController
    var compact = false
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            if compact {
                Text(String(format: "Buffer ahead: %.1fs", playback.bufferAheadSeconds)).foregroundStyle(.green)
            } else { HStack {
                Text(String(format: "Buffer ahead: %.1fs", playback.bufferAheadSeconds))
                Spacer()
                Text(playback.liveBufferChoice.rawValue == 0 ? "Real time · no added wait" : "Goal: \(playback.liveBufferChoice.rawValue)s")
            } }
            ProgressView(value: min(playback.bufferAheadSeconds, playback.liveBufferChoice.preferredForwardDuration), total: playback.liveBufferChoice.preferredForwardDuration).tint(.green)
            Text(playback.behindAvailableEdgeSeconds.map { String(format: compact ? "Behind edge: %.1fs" : "Behind available edge: %.1fs", $0) } ?? (compact ? "Live edge unknown" : "Live edge unknown — provider has not supplied a usable window"))
                .foregroundStyle(.secondary)
        }.font(.caption2).padding(.horizontal, compact ? 0 : 16).accessibilityElement(children: .combine)
    }
}
