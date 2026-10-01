import SwiftUI
struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    @State private var signOut = false
    var body: some View {
        Form {
            Section { BrandHeader(); Text("Apple preview 0.1 • Based on RYZOD 4.70").font(.caption).foregroundStyle(.secondary) }
            Section("Connection") {
                Text(model.profile?.kind == .m3u ? "Playlist link" : "Provider login")
                Text("Credentials are stored securely on this device.").font(.caption).foregroundStyle(.secondary)
                Button("Sign out", role: .destructive) { signOut = true }
                if let error = model.loginError { Text(error).foregroundStyle(.orange) }
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
