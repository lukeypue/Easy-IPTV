import SwiftUI
@main struct RyzodApp: App {
    @StateObject private var model = AppModel()
    @StateObject private var playback = PlaybackController()
    var body: some Scene {
        WindowGroup {
            Group { if model.connected { RootView() } else { NavigationStack { LoginView() } } }
                .environmentObject(model).environmentObject(playback)
                .preferredColorScheme(.dark).tint(Theme.cyan)
                .task { await model.restore() }
        }
    }
}
