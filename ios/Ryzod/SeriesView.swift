import SwiftUI
import RyzodCore
struct SeriesView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    let series: MediaItem
    @State private var episodes: [Episode] = []
    @State private var loading = true
    @State private var error: String?
    @State private var selectedSeason = -1
    var seasons: [Int] { Array(Set(episodes.map(\.season))).sorted() }
    var body: some View {
        VStack {
            if loading { ProgressView("Loading episodes…").padding() }
            if let error { Text(error).padding(); Button("Retry") { Task { await load() } } }
            if !seasons.isEmpty {
                Picker("Season", selection: $selectedSeason) { Text("All seasons").tag(-1); ForEach(seasons, id: \.self) { Text("Season \($0)").tag($0) } }.pickerStyle(.menu)
            }
            List(episodes.filter { selectedSeason == -1 || $0.season == selectedSeason }) { episode in
                Button {
                    let item = MediaItem(id: "episode_\(episode.id)", name: "\(series.name) — \(episode.title)", kind: .movie, artwork: series.artwork, streamURL: episode.url)
                    playback.play(item)
                } label: { VStack(alignment: .leading, spacing: 5) { Text(episode.title).foregroundStyle(.primary); Text("Season \(episode.season) • Episode \(episode.number)").font(.caption).foregroundStyle(.secondary) } }
            }.listStyle(.plain).scrollContentBackground(.hidden)
            if !loading && error == nil && episodes.isEmpty { Text("Your provider returned no episodes.").foregroundStyle(.secondary).padding() }
        }.background(Theme.background).navigationTitle(series.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { model.toggleFavorite(series) } label: { Image(systemName: model.favorites.contains(series.favoriteKey) ? "star.fill" : "star") }.accessibilityLabel("Toggle series favorite") } }
            .task { await load() }
    }
    func load() async {
        guard let provider = model.client else { loading = false; return }
        loading = true; error = nil
        do {
            let loaded = try await provider.episodes(seriesID: series.id)
            try Task.checkCancellation(); episodes = loaded
        } catch is CancellationError { }
        catch { self.error = model.safeMessage(error) }
        loading = false
    }
}
