import SwiftUI
import RyzodCore
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    @State private var signOut = false
    var body: some View {
        Form {
            Section { BrandHeader(); Text("Apple preview 0.2 • Updated for RYZOD 4.80").font(.caption).foregroundStyle(.secondary) }
            Section("Connection") {
                Text(model.profile?.kind == .m3u ? "Playlist link" : "Provider login")
                Text("Credentials are stored securely on this device.").font(.caption).foregroundStyle(.secondary)
                Button("Sign out", role: .destructive) { signOut = true }
                if let error = model.loginError { Text(error).foregroundStyle(.orange) }
            }
            Section("Live buffer") {
                Picker("Forward buffer goal", selection: $playback.liveBufferChoice) {
                    ForEach(LiveBufferChoice.allCases, id: \.rawValue) { choice in Text(choice.label).tag(choice) }
                }
                Text("Real time requests immediate startup; provider delay and necessary decoder buffering remain. Apple still manages recovery after a stall. Other choices request more video ahead. A 20-second goal does not prove that 20 seconds are available. Memory and provider delivery can limit it.").font(.footnote).foregroundStyle(.secondary)
                Text("Changing this stops the live preview and applies to the next channel. Check Buffer ahead in the guide/player. Roku and Apple use their native recovery controls; Android's Natural/Steady thresholds are not available here.").font(.footnote).foregroundStyle(.secondary)
            }
            Section("This Apple preview") {
                Text("Live TV, provider guide, movies, series, search, and favorites.")
                Text("Playback uses Apple's built-in media support. Some provider formats may not play.").font(.footnote)
                Text("Local DVR, scheduled recording, offline downloads, external USB storage, and Apple TV are not included in this first preview.").font(.footnote).foregroundStyle(.secondary)
            }
            Section("Support") { Link("RYZOD website", destination: URL(string: "https://ryzod.com")!) }
            Section { Text("RYZOD does not provide channels, movies, playlists, or subscriptions. Connect your own authorized media source.").font(.footnote).foregroundStyle(.secondary) }
        }.navigationTitle("Settings").confirmationDialog("Sign out of your provider?", isPresented: $signOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { playback.stop(); model.signOut() }
            Button("Close", role: .cancel) { }
        } message: { Text("Saved provider credentials will be removed from this iPhone.") }
    }
}
