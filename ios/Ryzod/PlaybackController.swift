import SwiftUI
import AVFoundation
import RyzodCore
@MainActor final class PlaybackController: ObservableObject {
    let player = AVPlayer()
    @Published var current: MediaItem?
    @Published var fullScreen = false
    @Published var failure: String?
    @Published var waiting = false
    private(set) var previousLive: MediaItem?
    private var observation: NSKeyValueObservation?
    private var waitingObservation: NSKeyValueObservation?
    init() {
        player.automaticallyWaitsToMinimizeStalling = true
        waitingObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            Task { @MainActor in self?.waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate }
        }
    }
    func play(_ item: MediaItem, fullScreen: Bool = true) {
        guard let url = item.streamURL else { return }
        if current?.favoriteKey == item.favoriteKey, current?.streamURL == url {
            self.fullScreen = fullScreen; player.play(); return
        }
        if current?.kind == .live { previousLive = current }
        observation = nil; player.pause(); player.replaceCurrentItem(with: nil)
        current = item; failure = nil; self.fullScreen = fullScreen
        do { try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback); try AVAudioSession.sharedInstance().setActive(true) }
        catch { failure = "Audio could not start. Try playing again." }
        let asset = AVURLAsset(url: url)
        let next = AVPlayerItem(asset: asset); next.preferredForwardBufferDuration = 8
        observation = next.observe(\.status, options: [.new]) { [weak self] observed, _ in
            guard observed.status == .failed else { return }
            Task { @MainActor in
                guard self?.player.currentItem === observed else { return }
                self?.failure = "This stream could not play. Check Wi-Fi and provider access. This Apple preview needs an Apple-compatible video and audio format."
            }
        }
        player.replaceCurrentItem(with: next); player.play()
    }
    func retry() { guard let item = current else { return }; let full = fullScreen; stop(); play(item, fullScreen: full) }
    func stop() {
        observation = nil; player.pause(); player.replaceCurrentItem(with: nil)
        current = nil; previousLive = nil; waiting = false; failure = nil; fullScreen = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    func leaveFullScreen() {
        fullScreen = false
        if current?.kind != .live { stop() }
    }
}
