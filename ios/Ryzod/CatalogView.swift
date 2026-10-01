import SwiftUI
import RyzodCore
struct CatalogView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    let kind: MediaKind
    @State private var query = ""
    @State private var category = "all"
    @State private var favoritesOnly = false
    @State private var selected: MediaItem?
    @State private var options = false
    var filtered: [MediaItem] {
        (model.items[kind] ?? []).filter {
            (category == "all" || $0.categoryID == category) && (!favoritesOnly || model.favorites.contains($0.favoriteKey)) &&
            (query.isEmpty || ($0.name + " " + $0.searchText).localizedCaseInsensitiveContains(query))
        }
    }
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Picker("Category", selection: $category) {
                    Text("All categories").tag("all"); ForEach(model.categories[kind] ?? []) { Text($0.name).tag($0.id) }
                }.pickerStyle(.menu)
                Spacer(); Toggle(isOn: $favoritesOnly) { Image(systemName: "star.fill") }.toggleStyle(.button).accessibilityLabel("Show favorites only")
            }.padding(.horizontal)
            if model.loading.contains(kind) { ProgressView("Loading from your provider…").padding() }
            if let error = model.catalogErrors[kind] { VStack { Text(error).font(.callout); Button("Retry") { model.loadCatalog(kind, force: true) } }.padding() }
            List(filtered) { item in
                if kind == .series { NavigationLink { SeriesView(series: item) } label: { MediaRow(item: item) }.contextMenu { favoriteButton(item) } }
                else { Button { selected = item; options = true } label: { MediaRow(item: item) }.buttonStyle(.plain) }
            }.listStyle(.plain).scrollContentBackground(.hidden)
            if filtered.isEmpty && !model.loading.contains(kind) && model.catalogErrors[kind] == nil {
                Text(kind == .series && model.profile?.kind == .m3u ? "This playlist has no separate series catalog." : "No titles match this selection.").foregroundStyle(.secondary).padding()
            }
        }.background(Theme.background).navigationTitle(kind == .movie ? "Movies" : "Series")
            .searchable(text: $query, prompt: "Search \(kind == .movie ? "movies" : "series")")
            .task { model.loadCatalog(kind) }
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button { model.loadCatalog(kind, force: true) } label: { Image(systemName: "arrow.clockwise") }.disabled(model.loading.contains(kind)).accessibilityLabel("Refresh catalog") } }
            .confirmationDialog(selected?.name ?? "Movie", isPresented: $options, titleVisibility: .visible) {
                if let selected { Button("Play") { playback.play(selected) }; favoriteButton(selected) }
                Button("Close", role: .cancel) { }
            }
    }
    func favoriteButton(_ item: MediaItem) -> some View { Button(model.favorites.contains(item.favoriteKey) ? "Remove Favorite" : "Favorite") { model.toggleFavorite(item) } }
}
struct MediaRow: View {
    @EnvironmentObject var model: AppModel
    let item: MediaItem
    var body: some View {
        HStack(spacing: 12) {
            Artwork(item: item, width: 46, height: 64)
            VStack(alignment: .leading, spacing: 6) { Text(item.name).font(.headline).foregroundStyle(.primary); Text(item.kind.rawValue.capitalized).font(.caption).foregroundStyle(.secondary) }
            Spacer(); if model.favorites.contains(item.favoriteKey) { Image(systemName: "star.fill").foregroundStyle(.yellow) }
        }.padding(.vertical, 4)
    }
}
