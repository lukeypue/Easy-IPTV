import SwiftUI
import CryptoKit
import RyzodCore
@MainActor final class AppModel: ObservableObject {
    @Published var profile: ProviderProfile?
    @Published var connected = false
    @Published var connecting = false
    @Published var loginError: String?
    @Published var items: [MediaKind: [MediaItem]] = [:]
    @Published var categories: [MediaKind: [RyzodCore.Category]] = [:]
    @Published var loading: Set<MediaKind> = []
    @Published var catalogErrors: [MediaKind: String] = [:]
    @Published var guide: [String: [Program]] = [:]
    @Published var guideLoading = false
    @Published var guideStatus = "Program information comes directly from your provider."
    @Published var selectedLiveID: String?
    @Published var favorites: Set<String> = []
    @Published var catalogRevision = 0
    private var epoch = UUID()
    private var catalogTasks: [MediaKind: Task<Void, Never>] = [:]
    private var epgTasks: [String: Task<Void, Never>] = [:]
    private var epgQueue: [MediaItem] = []
    private var guideEpoch = UUID()
    private var guideTask: Task<Void, Never>?
    private var guideURL: URL?
    private var favoriteStoreKey = ""
    private let transport: ProviderClient.Transport
    init(transport: @escaping ProviderClient.Transport = ProviderClient.fetch) { self.transport = transport }
    let uiTesting = ProcessInfo.processInfo.arguments.contains("--ui-testing")
    var client: ProviderClient? { profile.flatMap { $0.kind == .xtream ? ProviderClient(credentials: $0.credentials, transport: transport) : nil } }
    func restore() async {
        guard !uiTesting else { return }
        do { if let saved = try CredentialStore.load() { await connect(saved, persist: false) } }
        catch { loginError = safeMessage(error) }
    }
    func connect(_ candidate: ProviderProfile, persist: Bool = true) async {
        guard !connecting else { return }
        cancelRequests(); epoch = UUID(); let ticket = epoch
        connecting = true; loginError = nil
        defer { if ticket == epoch { connecting = false } }
        do {
            var media: [MediaItem]; var cats: [RyzodCore.Category]; var playlist: PlaylistResult?
            if candidate.kind == .xtream {
                _ = try candidate.credentials.api()
                let provider = ProviderClient(credentials: candidate.credentials, transport: transport)
                try await provider.authenticate()
                media = try await provider.media(.live)
                cats = (try? await provider.categories(.live)) ?? []
            } else {
                let url = try ProviderURL.validated(candidate.playlistURL)
                let data = try await transport(url)
                playlist = try await Task.detached { try M3UParser.parse(String(decoding: data, as: UTF8.self), baseURL: url) }.value
                media = playlist!.items.filter { $0.kind == .live }; cats = playlist!.categories
            }
            try Task.checkCancellation(); guard ticket == epoch else { return }
            if persist && !uiTesting { try CredentialStore.save(candidate) }
            profile = candidate; items = [.live: media]; categories = [.live: cats]
            if let playlist { items[.movie] = playlist.items.filter { $0.kind == .movie }; categories[.movie] = cats; items[.series] = []; categories[.series] = [] }
            guideURL = playlist?.guideURL; guide = [:]; guideStatus = "Program information comes directly from your provider."
            favoriteStoreKey = "ryzod.favorites." + SHA256.hash(data: Data((candidate.server + "|" + candidate.username + "|" + candidate.playlistURL).utf8)).map { String(format: "%02x", $0) }.joined()
            favorites = Set(UserDefaults.standard.stringArray(forKey: favoriteStoreKey) ?? [])
            connected = true; catalogRevision += 1
            if guideURL != nil { refreshGuide() }
        } catch is CancellationError { }
        catch { if ticket == epoch { loginError = safeMessage(error) } }
    }
    func loadCatalog(_ kind: MediaKind, force: Bool = false) {
        guard connected, let provider = client, !loading.contains(kind), force || items[kind] == nil else { return }
        let ticket = epoch
        loading.insert(kind); catalogErrors[kind] = nil
        catalogTasks[kind] = Task {
            defer { if ticket == epoch { loading.remove(kind); catalogTasks[kind] = nil } }
            do {
                let loaded = try await provider.media(kind)
                let cats = (try? await provider.categories(kind)) ?? []
                try Task.checkCancellation(); guard ticket == epoch else { return }
                items[kind] = loaded; categories[kind] = cats; catalogRevision += 1
            } catch is CancellationError { }
            catch { if ticket == epoch { catalogErrors[kind] = safeMessage(error) } }
        }
    }
    func loadGuide(for item: MediaItem, force: Bool = false) {
        guard connected, item.kind == .live, client != nil, epgTasks[item.id] == nil,
              !epgQueue.contains(where: { $0.id == item.id }), force || guide[item.id] == nil else { return }
        epgQueue.append(item)
        drainGuideQueue()
    }
    private func drainGuideQueue() {
        guard connected, !guideLoading, let provider = client else { return }
        while epgTasks.count < 3, !epgQueue.isEmpty {
            let item = epgQueue.removeFirst()
            let ticket = epoch; let guideTicket = guideEpoch
            epgTasks[item.id] = Task {
                defer {
                    if ticket == epoch && guideTicket == guideEpoch {
                        epgTasks[item.id] = nil
                        drainGuideQueue()
                    }
                }
                do {
                    let programs = try await provider.epg(channelID: item.id)
                    try Task.checkCancellation()
                    guard ticket == epoch, guideTicket == guideEpoch else { return }
                    guide[item.id] = programs
                } catch is CancellationError { }
                catch {
                    if ticket == epoch && guideTicket == guideEpoch {
                        guideStatus = "Some guide information could not load. Your previous guide is kept. Tap Update Guide to retry."
                    }
                }
            }
        }
    }
    func refreshGuide() {
        guard connected, !guideLoading else { return }
        // Include cached, selected and requested rows even when they are outside the first category.
        var requested = Set(guide.keys).union(epgTasks.keys).union(epgQueue.map(\.id))
        if let selectedLiveID { requested.insert(selectedLiveID) }
        requested.formUnion((items[.live] ?? []).prefix(20).map(\.id))
        let channels = (items[.live] ?? []).filter { requested.contains($0.id) }
        epgTasks.values.forEach { $0.cancel() }; epgTasks = [:]; epgQueue = []
        guideEpoch = UUID(); let guideTicket = guideEpoch
        guideLoading = true; guideStatus = "Updating from your provider. This may take 1–2 minutes or longer."
        let ticket = epoch; let url = guideURL; let provider = client
        guideTask = Task {
            defer {
                if ticket == epoch && guideTicket == guideEpoch {
                    guideLoading = false; guideTask = nil; drainGuideQueue()
                }
            }
            do {
                var failedChannels = 0
                if let url {
                    let data = try await transport(url)
                    let now = Date()
                    let parsed = try await Task.detached { try XMLTVParser.parse(data, from: now.addingTimeInterval(-3600), until: now.addingTimeInterval(36 * 3600)) }.value
                    try Task.checkCancellation()
                    guard ticket == epoch, guideTicket == guideEpoch else { return }
                    var mapped: [String: [Program]] = [:]
                    for item in items[.live] ?? [] { mapped[item.id] = parsed[item.epgID ?? ""] ?? parsed[item.name] ?? [] }
                    guide = mapped
                } else if let provider {
                    var refreshed: [String: [Program]] = [:]
                    for item in channels {
                        try Task.checkCancellation()
                        do { refreshed[item.id] = try await provider.epg(channelID: item.id) }
                        catch is CancellationError { throw CancellationError() }
                        catch let error as URLError where error.code == .cancelled { throw CancellationError() }
                        catch { failedChannels += 1 } // Retain old programs on a failed channel.
                    }
                    try Task.checkCancellation()
                    guard ticket == epoch, guideTicket == guideEpoch else { return }
                    guard !refreshed.isEmpty || channels.isEmpty else { throw ProviderError.invalidResponse }
                    guide.merge(refreshed) { _, new in new }
                } else { guideStatus = "This playlist does not include a guide link."; return }
                guideStatus = failedChannels == 0
                    ? "Guide updated from your provider at \(Date().formatted(date: .omitted, time: .shortened))."
                    : "Guide updated. Some channels could not refresh; their previous guide is kept. Tap Update Guide to retry."
            } catch is CancellationError { }
            catch {
                if ticket == epoch && guideTicket == guideEpoch {
                    guideStatus = "Guide refresh failed. Your previous guide is kept. Please try again."
                }
            }
        }
    }
    func toggleFavorite(_ item: MediaItem) {
        if favorites.contains(item.favoriteKey) { favorites.remove(item.favoriteKey) } else { favorites.insert(item.favoriteKey) }
        if !uiTesting { UserDefaults.standard.set(Array(favorites).sorted(), forKey: favoriteStoreKey) }
    }
    func signOut() {
        do { if !uiTesting { try CredentialStore.clear() } }
        catch { loginError = safeMessage(error); return }
        cancelRequests(); epoch = UUID(); connecting = false
        connected = false; profile = nil; items = [:]; categories = [:]; guide = [:]; favorites = []
        selectedLiveID = nil; guideURL = nil; catalogErrors = [:]; catalogRevision += 1
    }
    private func cancelRequests() {
        catalogTasks.values.forEach { $0.cancel() }; epgTasks.values.forEach { $0.cancel() }; guideTask?.cancel()
        catalogTasks = [:]; epgTasks = [:]; epgQueue = []; guideTask = nil; guideEpoch = UUID(); loading = []; guideLoading = false
    }
    func safeMessage(_ error: Error) -> String {
        if error is ProviderError || error is CredentialStore.StoreError { return error.localizedDescription }
        if let network = error as? URLError {
            switch network.code {
            case .notConnectedToInternet, .networkConnectionLost: return "The connection was lost. Check Wi-Fi and try again."
            case .timedOut: return "The provider took too long to respond. Please try again."
            default: return "Could not reach your provider. Check its address and your connection."
            }
        }
        return "The provider response could not be loaded. Please try again."
    }
}
