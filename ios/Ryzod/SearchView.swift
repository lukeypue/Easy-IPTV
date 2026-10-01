import SwiftUI
import RyzodCore
struct SearchView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    @State private var query = ""
    @State private var results: [MediaItem] = []
    @State private var searchTask: Task<Void, Never>?
    @State private var selected: MediaItem?
    @State private var options = false
    var body: some View {
        VStack {
            if query.isEmpty { VStack(spacing: 14) { Image(systemName: "magnifyingglass").font(.largeTitle).foregroundStyle(Theme.cyan); Text("Find channels, movies, and series."); Text("Catalogs load when you search. Typing searches your loaded titles locally.").font(.caption).foregroundStyle(.secondary) }.padding() }
            if !model.loading.isEmpty { ProgressView("Loading catalog…").font(.caption) }
            List {
                ForEach(results, id: \.favoriteKey) { item in
                    if item.kind == .series { NavigationLink { SeriesView(series: item) } label: { MediaRow(item: item) } }
                    else { Button { selected = item; options = true } label: { MediaRow(item: item) }.buttonStyle(.plain) }
                }
                if !query.isEmpty && results.isEmpty && model.loading.isEmpty { Text("No matching titles in your loaded catalogs.").foregroundStyle(.secondary) }
                if results.count == 200 { Text("Showing the first 200 matches. Add more words to narrow your search.").font(.caption).foregroundStyle(.secondary) }
                ForEach(MediaKind.allCases, id: \.self) { kind in
                    if let error = model.catalogErrors[kind] { VStack(alignment: .leading) { Text("\(kind.rawValue.capitalized): \(error)").font(.caption); Button("Retry") { model.loadCatalog(kind, force: true) } } }
                }
            }.listStyle(.plain).scrollContentBackground(.hidden)
        }.background(Theme.background).navigationTitle("Search").searchable(text: $query, prompt: "Search your provider's titles")
            .onChange(of: query) { _ in
                if !query.isEmpty { model.loadCatalog(.movie); model.loadCatalog(.series) }
                search()
            }.onChange(of: model.catalogRevision) { _ in search() }
            .onDisappear { searchTask?.cancel() }
            .confirmationDialog(selected?.name ?? "Title", isPresented: $options, titleVisibility: .visible) {
                if let selected {
                    Button(selected.kind == .live ? "Watch" : "Play") { if selected.kind == .live { model.selectedLiveID = selected.id }; playback.play(selected) }
                    Button(model.favorites.contains(selected.favoriteKey) ? "Remove Favorite" : "Favorite") { model.toggleFavorite(selected) }
                }
                Button("Close", role: .cancel) { }
            }
    }
    func search() {
        searchTask?.cancel()
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if needle.isEmpty { results = []; return }
        let snapshot = MediaKind.allCases.flatMap { model.items[$0] ?? [] }
        searchTask = Task {
            do { try await Task.sleep(nanoseconds: 180_000_000) } catch { return }
            let matches = await Task.detached {
                Array(snapshot.lazy.filter { ($0.name + " " + $0.searchText).localizedCaseInsensitiveContains(needle) }.prefix(200))
            }.value
            guard !Task.isCancelled, needle == query.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
            results = matches
        }
    }
}
