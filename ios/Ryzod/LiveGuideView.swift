import SwiftUI
import AVKit
import RyzodCore
struct LiveGuideView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    @State private var category = "all"
    @State private var favoritesOnly = false
    @State private var query = ""
    @State private var selected: MediaItem?
    @State private var selectedProgram: Program?
    @State private var options = false
    @State private var windowStart = Self.rounded(Date())
    private let pixelsPerMinute: CGFloat = 4
    static func rounded(_ date: Date) -> Date { Date(timeIntervalSince1970: floor(date.timeIntervalSince1970 / 1800) * 1800) }
    var channels: [MediaItem] {
        (model.items[.live] ?? []).filter { item in
            (category == "all" || item.categoryID == category) && (!favoritesOnly || model.favorites.contains(item.favoriteKey)) && (query.isEmpty || item.name.localizedCaseInsensitiveContains(query))
        }
    }
    var body: some View {
        VStack(spacing: 8) {
            HStack { BrandHeader(); Spacer(); Button { model.refreshGuide() } label: { Label("Update Guide", systemImage: "arrow.clockwise") }.font(.caption).disabled(model.guideLoading) }.padding(.horizontal)
            Text(model.guideStatus).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal)
            if let current = playback.current, current.kind == .live, !playback.fullScreen {
                HStack {
                    VideoPlayer(player: playback.player).frame(width: 150, height: 84).overlay(alignment: .topTrailing) { Button { playback.fullScreen = true } label: { Image(systemName: "arrow.up.left.and.arrow.down.right").padding(8).background(.black.opacity(0.6)) }.accessibilityLabel("Open current channel full screen") }
                    VStack(alignment: .leading) { Text(current.name).font(.headline).lineLimit(2); Button("Full screen") { playback.fullScreen = true } }
                    Spacer(); Button { playback.stop() } label: { Image(systemName: "stop.fill") }.accessibilityLabel("Stop preview")
                }.padding(.horizontal)
            }
            HStack {
                Picker("Category", selection: $category) {
                    Text("All channels").tag("all")
                    ForEach(model.categories[.live] ?? []) { Text($0.name).tag($0.id) }
                }.pickerStyle(.menu)
                Spacer(); Toggle(isOn: $favoritesOnly) { Image(systemName: "star.fill").foregroundStyle(.yellow) }.toggleStyle(.button).accessibilityLabel("Show favorites only")
            }.padding(.horizontal)
            HStack {
                Button { windowStart = windowStart.addingTimeInterval(-7200) } label: { Image(systemName: "chevron.left") }.accessibilityLabel("Earlier programs")
                Spacer(); Text(windowStart.formatted(date: .abbreviated, time: .shortened)).font(.caption)
                Spacer(); Button("Now") { windowStart = Self.rounded(Date()) }
                Button { windowStart = windowStart.addingTimeInterval(7200) } label: { Image(systemName: "chevron.right") }.accessibilityLabel("Later programs")
            }.padding(.horizontal)
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(spacing: 0) {
                            Text("CHANNEL").font(.caption.bold()).frame(width: 150, alignment: .leading).padding(.leading, 8)
                            ForEach(0..<4) { offset in Text(windowStart.addingTimeInterval(Double(offset) * 1800).formatted(date: .omitted, time: .shortened)).font(.caption.bold()).frame(width: 120, alignment: .leading) }
                        }.frame(height: 32).background(Theme.panel)
                        Rectangle().fill(.white.opacity(0.65)).frame(height: 1)
                        ScrollView(.vertical) {
                            LazyVStack(spacing: 0) {
                                ForEach(channels) { channel in
                                    row(channel).id(channel.id).onAppear { model.loadGuide(for: channel) }
                                }
                            }
                        }
                    }.frame(width: 638)
                }.onAppear { if let id = model.selectedLiveID { proxy.scrollTo(id, anchor: .center) } }
                    .onChange(of: playback.fullScreen) { full in if !full, let id = model.selectedLiveID { proxy.scrollTo(id, anchor: .center) } }
            }
            if channels.isEmpty { Text(favoritesOnly ? "Favorite a channel to see it here." : "No channels match this selection.").foregroundStyle(.secondary).padding() }
        }.background(Theme.background).navigationBarHidden(true).searchable(text: $query, prompt: "Search channels")
            .confirmationDialog(selectedProgram?.title.isEmpty == false ? selectedProgram!.title : selected?.name ?? "Channel", isPresented: $options, titleVisibility: .visible) {
                if let selected {
                    Button("Watch") { model.selectedLiveID = selected.id; playback.play(selected) }
                    Button(model.favorites.contains(selected.favoriteKey) ? "Remove Favorite" : "Favorite") { model.toggleFavorite(selected) }
                }
                Button("Close", role: .cancel) { }
            } message: { Text(selectedProgram?.detail.isEmpty == false ? selectedProgram!.detail : "Guide information is supplied by your provider.") }
    }
    func row(_ channel: MediaItem) -> some View {
        HStack(spacing: 0) {
            Button { showOptions(channel, nil) } label: {
                HStack(spacing: 6) { Artwork(item: channel, width: 32, height: 32); Text(channel.name).font(.caption).lineLimit(2); if model.favorites.contains(channel.favoriteKey) { Image(systemName: "star.fill").font(.caption2).foregroundStyle(.yellow) } }
                    .frame(width: 142, height: 64, alignment: .leading).padding(.horizontal, 8)
            }.buttonStyle(.plain).background(model.selectedLiveID == channel.id ? Theme.cyan.opacity(0.15) : Theme.panel)
            Rectangle().fill(.white.opacity(0.3)).frame(width: 1)
            HStack(spacing: 0) {
                ForEach(GuideGeometry.cells(programs: model.guide[channel.id] ?? [], start: windowStart, end: windowStart.addingTimeInterval(7200))) { cell in
                    Button { showOptions(channel, cell.program) } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(cell.program?.title.isEmpty == false ? cell.program!.title : "No guide information").font(.caption.bold()).lineLimit(2)
                            if let program = cell.program { Text(program.start.formatted(date: .omitted, time: .shortened)).font(.caption2).foregroundStyle(.secondary) }
                        }.padding(.horizontal, 6).frame(width: max(1, cell.duration / 60 * pixelsPerMinute), height: 64, alignment: .leading)
                            .background(cell.program == nil ? Color.clear : Theme.panel.opacity(0.65)).overlay(alignment: .trailing) { Rectangle().fill(.white.opacity(0.3)).frame(width: 1) }.clipped()
                    }.buttonStyle(.plain)
                }
            }
        }.frame(height: 65).overlay(alignment: .bottom) { Rectangle().fill(.white.opacity(0.2)).frame(height: 1) }
    }
    func showOptions(_ channel: MediaItem, _ program: Program?) { selected = channel; selectedProgram = program; model.selectedLiveID = channel.id; options = true }
}
