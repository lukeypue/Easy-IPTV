import SwiftUI
import RyzodCore
enum Theme {
    static let background = Color(red: 0.025, green: 0.035, blue: 0.075)
    static let panel = Color(red: 0.065, green: 0.085, blue: 0.145)
    static let cyan = Color(red: 0.2, green: 0.88, blue: 1)
    static let pink = Color(red: 1, green: 0.18, blue: 0.72)
}
struct BrandHeader: View {
    var body: some View {
        HStack(spacing: 10) {
            Image("Brand").resizable().scaledToFit().frame(width: 48, height: 48).accessibilityHidden(true)
            VStack(alignment: .leading) { Text("RYZOD").font(.headline).tracking(3); Text("MEDIA PLAYER").font(.caption2).tracking(2).foregroundStyle(Theme.cyan) }
        }.accessibilityElement(children: .combine)
    }
}
struct Artwork: View {
    let item: MediaItem; var width: CGFloat = 48; var height: CGFloat = 48
    var body: some View {
        AsyncImage(url: item.artwork) { image in image.resizable().scaledToFit() } placeholder: {
            Image(systemName: item.kind == .live ? "tv" : "play.rectangle").font(.title2).foregroundStyle(Theme.cyan)
        }.frame(width: width, height: height).background(Theme.panel).clipShape(RoundedRectangle(cornerRadius: 8)).accessibilityHidden(true)
    }
}
