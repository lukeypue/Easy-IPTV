import SwiftUI
import AVKit
import RyzodCore
struct PlayerView: View {
    @EnvironmentObject var playback: PlaybackController
    @EnvironmentObject var model: AppModel
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button { playback.leaveFullScreen() } label: { Label("Guide", systemImage: "chevron.left") }
                Spacer(); Text(playback.current?.name ?? "RYZOD").lineLimit(1); Spacer()
                Button { playback.stop() } label: { Image(systemName: "stop.fill") }.accessibilityLabel("Stop playback")
            }.padding().background(Theme.background)
            ZStack {
                VideoPlayer(player: playback.player)
                if playback.waiting { ProgressView("Connecting…").padding().background(.ultraThinMaterial).clipShape(RoundedRectangle(cornerRadius: 12)) }
                if let error = playback.failure { VStack(spacing: 14) { Text(error).multilineTextAlignment(.center); Button("Retry") { playback.retry() } }.padding().frame(maxWidth: 400).background(Theme.panel).clipShape(RoundedRectangle(cornerRadius: 12)) }
            }.frame(maxHeight: .infinity)
            if let item = playback.current {
                HStack {
                    if item.kind == .live, let previous = playback.previousLive {
                        Button { model.selectedLiveID = previous.id; playback.play(previous) } label: { Label("Previous", systemImage: "backward.end") }
                    }
                    Spacer()
                    Button { model.toggleFavorite(item) } label: { Label(model.favorites.contains(item.favoriteKey) ? "Favorited" : "Favorite", systemImage: model.favorites.contains(item.favoriteKey) ? "star.fill" : "star") }
                }.padding()
                if item.kind == .live { Text("Live pause depends on the provider's stream. Local DVR recording is not included in this first Apple preview.").font(.caption).foregroundStyle(.secondary).padding(.horizontal).padding(.bottom, 10) }
            }
        }.background(Color.black)
    }
}
