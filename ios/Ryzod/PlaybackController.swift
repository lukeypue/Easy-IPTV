import SwiftUI
import AVFoundation
import RyzodCore
@MainActor final class PlaybackController: ObservableObject {
    let player = AVPlayer()
    private let defaults: UserDefaults
    @Published var liveBufferChoice: LiveBufferChoice {
        didSet {
            if liveBufferChoice != oldValue {
                defaults.set(liveBufferChoice.rawValue, forKey: "live_buffer_seconds")
                if current?.kind == .live { stop() }
            }
        }
    }
    @Published private(set) var bufferAheadSeconds = 0.0
    @Published private(set) var behindAvailableEdgeSeconds: Double?
    @Published var current: MediaItem?
    @Published var fullScreen = false
    @Published var failure: String?
    @Published var waiting = false
    private(set) var previousLive: MediaItem?
    private var observation: NSKeyValueObservation?
    private var waitingObservation: NSKeyValueObservation?
    private var bufferObservation: NSKeyValueObservation?
    private var edgeObservation: NSKeyValueObservation?
    private var healthObserver: Any?
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        liveBufferChoice = LiveBufferChoice(savedSeconds: defaults.object(forKey: "live_buffer_seconds") as? Int ?? 30)
        player.automaticallyWaitsToMinimizeStalling = true
        healthObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] _ in
            Task { @MainActor in self?.refreshBufferHealth() }
        }
        waitingObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            Task { @MainActor in self?.waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate }
        }
    }
    func play(_ item: MediaItem, fullScreen: Bool = true) {
        guard let url = item.streamURL else { return }
        if current?.favoriteKey == item.favoriteKey, current?.streamURL == url {
            self.fullScreen = fullScreen; startRequestedPlayback(); return
        }
        if current?.kind == .live { previousLive = current }
        observation = nil; bufferObservation = nil; edgeObservation = nil
        bufferAheadSeconds = 0; behindAvailableEdgeSeconds = nil
        player.pause(); player.replaceCurrentItem(with: nil)
        current = item; failure = nil; self.fullScreen = fullScreen
        do { try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback); try AVAudioSession.sharedInstance().setActive(true) }
        catch { failure = "Audio could not start. Try playing again." }
        let asset = AVURLAsset(url: url)
        let next = AVPlayerItem(asset: asset)
        // Keep automatic recovery: disabling this can leave a stalled live feed paused.
        player.automaticallyWaitsToMinimizeStalling = true
        next.preferredForwardBufferDuration = item.kind == .live ? liveBufferChoice.preferredForwardDuration : 8
        next.automaticallyPreservesTimeOffsetFromLive = false
        bufferObservation = next.observe(\.loadedTimeRanges, options: [.new]) { [weak self] observed, _ in
            Task { @MainActor in
                guard self?.player.currentItem === observed else { return }
                self?.refreshBufferHealth()
            }
        }
        edgeObservation = next.observe(\.seekableTimeRanges, options: [.new]) { [weak self] observed, _ in
            Task { @MainActor in
                guard self?.player.currentItem === observed else { return }
                self?.refreshBufferHealth()
            }
        }
        observation = next.observe(\.status, options: [.new]) { [weak self] observed, _ in
            guard observed.status == .failed else { return }
            Task { @MainActor in
                guard self?.player.currentItem === observed else { return }
                self?.failure = "This stream could not play. Check Wi-Fi and provider access. This Apple preview needs an Apple-compatible video and audio format."
            }
        }
        player.replaceCurrentItem(with: next); startRequestedPlayback()
    }
    private func startRequestedPlayback() {
        if current?.kind == .live, liveBufferChoice.startsImmediately { player.playImmediately(atRate: 1) }
        else { player.play() }
    }
    private func refreshBufferHealth() {
        guard let item = player.currentItem else { return }
        let position = item.currentTime().seconds
        func intervals(_ values: [NSValue]) -> [BufferedInterval] {
            values.map { BufferedInterval(start: $0.timeRangeValue.start.seconds, duration: $0.timeRangeValue.duration.seconds) }
        }
        bufferAheadSeconds = PlaybackBufferHealth.ahead(position: position, ranges: intervals(item.loadedTimeRanges))
        behindAvailableEdgeSeconds = current?.kind == .live ? PlaybackBufferHealth.behindAvailableEdge(position: position, ranges: intervals(item.seekableTimeRanges)) : nil
    }
    deinit { if let healthObserver { player.removeTimeObserver(healthObserver) } }
    func retry() { guard let item = current else { return }; let full = fullScreen; stop(); play(item, fullScreen: full) }
    func stop() {
        observation = nil; bufferObservation = nil; edgeObservation = nil
        player.pause(); player.replaceCurrentItem(with: nil)
        bufferAheadSeconds = 0; behindAvailableEdgeSeconds = nil
        current = nil; previousLive = nil; waiting = false; failure = nil; fullScreen = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    func leaveFullScreen() {
        fullScreen = false
        if current?.kind != .live { stop() }
    }
}
