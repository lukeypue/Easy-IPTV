import SwiftUI
import RyzodCore
struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var playback: PlaybackController
    var body: some View {
        TabView {
            NavigationStack { LiveGuideView() }.tabItem { Label("Live", systemImage: "tv") }
            NavigationStack { CatalogView(kind: .movie) }.tabItem { Label("Movies", systemImage: "film") }
            NavigationStack { CatalogView(kind: .series) }.tabItem { Label("Series", systemImage: "rectangle.stack") }
            NavigationStack { SearchView() }.tabItem { Label("Search", systemImage: "magnifyingglass") }
            NavigationStack { SettingsView() }.tabItem { Label("Settings", systemImage: "gearshape") }
        }.fullScreenCover(isPresented: $playback.fullScreen, onDismiss: { playback.leaveFullScreen() }) { PlayerView() }
    }
}
