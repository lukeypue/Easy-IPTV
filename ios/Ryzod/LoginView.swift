import SwiftUI
struct LoginView: View {
    @EnvironmentObject var model: AppModel
    @State private var draft = ProviderProfile()
    @FocusState private var focused: Field?
    enum Field: Hashable { case server, username, password, playlist }
    var valid: Bool {
        draft.kind == .xtream ? !draft.server.trimmingCharacters(in: .whitespaces).isEmpty && !draft.username.isEmpty && !draft.password.isEmpty : !draft.playlistURL.trimmingCharacters(in: .whitespaces).isEmpty
    }
    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                Image("Brand").resizable().scaledToFit().frame(maxWidth: 220).accessibilityLabel("RYZOD Media Player")
                Text("Your media. Your player.").font(.title2.bold())
                Text("Connect your own provider or playlist.").foregroundStyle(.secondary)
                Picker("Connection type", selection: $draft.kind) {
                    Text("Provider login").tag(ProviderProfile.Kind.xtream)
                    Text("Playlist link").tag(ProviderProfile.Kind.m3u)
                }.pickerStyle(.segmented)
                VStack(spacing: 14) {
                    if draft.kind == .xtream {
                        TextField("Server address", text: $draft.server).keyboardType(.URL).textContentType(.URL).focused($focused, equals: .server).submitLabel(.next).onSubmit { focused = .username }
                        TextField("Username", text: $draft.username).textContentType(.username).focused($focused, equals: .username).submitLabel(.next).onSubmit { focused = .password }
                        SecureField("Password", text: $draft.password).textContentType(.password).focused($focused, equals: .password).submitLabel(.go).onSubmit { submit() }
                    } else { TextField("Playlist URL", text: $draft.playlistURL).keyboardType(.URL).focused($focused, equals: .playlist).submitLabel(.go).onSubmit { submit() } }
                }.textFieldStyle(.roundedBorder).textInputAutocapitalization(.never).autocorrectionDisabled()
                if let error = model.loginError { Text(error).foregroundStyle(.orange).font(.callout).accessibilityIdentifier("login-error") }
                Button(action: submit) { HStack { if model.connecting { ProgressView() }; Text(model.connecting ? "Connecting…" : "Connect").font(.headline); Spacer(); Image(systemName: "arrow.right") }.padding(10) }
                    .buttonStyle(.borderedProminent).disabled(!valid || model.connecting).accessibilityIdentifier("Connect")
                Text("RYZOD provides the player. It does not supply channels, movies, or subscriptions.").font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                Text("Apple preview • Based on RYZOD 4.70").font(.caption2).foregroundStyle(.secondary)
            }.padding(24).frame(maxWidth: 520).frame(maxWidth: .infinity)
        }.background(Theme.background).scrollDismissesKeyboard(.interactively)
            .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { focused = nil } } }
    }
    private func submit() {
        guard valid, !model.connecting else { return }; focused = nil
        let candidate = draft; Task { await model.connect(candidate) }
    }
}
