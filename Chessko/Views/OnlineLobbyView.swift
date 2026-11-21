import SwiftUI

// MARK: - Lobi igre preko interneta
//
// Prikazuje se unutar `OnlineGameView` dok ne postoji partija (stanja idle,
// connecting, lobby, inviting, invited). Sve odluke donosi `OnlineViewModel`;
// ovde se samo crta i prosleđuju radnje. `state == .idle` dok je ekran otvoren
// znači „povezujem" (kontrolor, tačka 1).

struct OnlineLobbyView: View {
    var vm: OnlineViewModel
    /// `false` kad uređaj nema mrežu (`NWPathMonitor` u `OnlineGameView`).
    var hasNetwork: Bool = true

    @State private var friendInput = ""

    private var isConnected: Bool {
        switch vm.state {
        case .lobby, .inviting, .invited: return true
        default: return false
        }
    }

    private var isInviting: Bool {
        if case .inviting = vm.state { return true }
        return false
    }

    private var connectionText: String? {
        if vm.updateRequired { return Loc("Za igru preko interneta potrebna je novija verzija aplikacije.") }
        if !hasNetwork && !isConnected { return Loc("Nema veze sa internetom.") }
        if !isConnected { return Loc("Povezujem…") }
        return nil
    }

    private var invitedCode: String? {
        if case .invited(let from, _) = vm.state { return from }
        return nil
    }

    var body: some View {
        ScrollView {
            VStack(spacing: DS.Space.l) {
                myCodeCard
                inviteCard
                if let text = connectionText {
                    statusLine(text, showsProgress: hasNetwork && !vm.updateRequired)
                }
                if let notice = vm.notice {
                    noticeLine(OnlineNoticeText.text(for: notice))
                }
            }
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, DS.Space.l)
            .padding(.vertical, DS.Space.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .task(id: vm.notice) {
            guard vm.notice != nil else { return }
            try? await Task.sleep(for: .seconds(4))
            if !Task.isCancelled { vm.notice = nil }
        }
        .alert(
            LocF("Kod %@ te poziva na partiju", OnlineCode.format(invitedCode ?? "")),
            isPresented: Binding(get: { invitedCode != nil }, set: { _ in })
        ) {
            Button(Loc("Prihvati")) { vm.accept() }
            Button(Loc("Odbij"), role: .cancel) { vm.decline() }
        }
    }

    // MARK: Tvoj kod

    private var myCodeCard: some View {
        VStack(spacing: DS.Space.m) {
            Text(Loc("Tvoj kod"))
                .font(.dsCaption.weight(.medium))
                .foregroundStyle(DS.inkMuted)
            Text(OnlineCode.format(vm.myCode))
                .font(.system(size: 40, weight: .semibold, design: .monospaced))
                .foregroundStyle(DS.ink)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .textSelection(.enabled)
                .accessibilityIdentifier("online.myCode")

            HStack(spacing: DS.Space.s) {
                Button {
                    UIPasteboard.general.string = OnlineCode.format(vm.myCode)
                    Haptics.impact(.light)
                } label: {
                    DSActionButtonLabel(systemImage: "doc.on.doc", title: Loc("Kopiraj"))
                }
                .buttonStyle(DSPressableButtonStyle())

                ShareLink(item: OnlineCode.format(vm.myCode)) {
                    DSActionButtonLabel(systemImage: "square.and.arrow.up", title: Loc("Podeli kod"))
                }
                .buttonStyle(DSPressableButtonStyle())
            }

            Button {
                vm.newCode()
            } label: {
                Text(Loc("Novi kod"))
            }
            .buttonStyle(DSTextButtonStyle())
        }
        .padding(DS.Space.l)
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    // MARK: Kod prijatelja

    private var inviteCard: some View {
        VStack(spacing: DS.Space.m) {
            if isInviting {
                HStack(spacing: DS.Space.s) {
                    ProgressView()
                    Text(Loc("Čekam da prijatelj prihvati…"))
                        .font(.dsBody)
                        .foregroundStyle(DS.ink)
                }
                .frame(maxWidth: .infinity, minHeight: 48)

                Button {
                    vm.cancelInvite()
                } label: {
                    Text(Loc("Otkaži"))
                }
                .buttonStyle(DSTextButtonStyle())
            } else {
                Text(Loc("Kod prijatelja"))
                    .font(.dsCaption.weight(.medium))
                    .foregroundStyle(DS.inkMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                TextField("", text: $friendInput, prompt: Text(verbatim: "K7M-4QX").foregroundStyle(DS.inkMuted))
                    .font(.system(size: 28, weight: .semibold, design: .monospaced))
                    .foregroundStyle(DS.ink)
                    .multilineTextAlignment(.center)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .keyboardType(.asciiCapable)
                    .padding(.horizontal, DS.Space.m)
                    .frame(minHeight: 56)
                    .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
                    .onChange(of: friendInput) { _, newValue in
                        let formatted = Self.formatInput(newValue)
                        if formatted != newValue { friendInput = formatted }
                    }
                    .accessibilityLabel(Loc("Kod prijatelja"))
                    .accessibilityIdentifier("online.friendCode")
                    .submitLabel(.go)
                    .onSubmit(invite)

                Button {
                    invite()
                } label: {
                    Text(Loc("Pozovi"))
                }
                .buttonStyle(DSPrimaryButtonStyle())
                .disabled(!isConnected || friendInput.isEmpty)
                .opacity(isConnected && !friendInput.isEmpty ? 1 : 0.38)
            }
        }
        .padding(DS.Space.l)
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    private func invite() {
        guard isConnected else { return }
        vm.invite(friendInput)
    }

    /// Velika slova, samo znaci azbuke koda, najvise 6, crtica posle trećeg.
    static func formatInput(_ input: String) -> String {
        let raw = String(input.uppercased().filter { OnlineCode.alphabet.contains($0) }.prefix(OnlineCode.length))
        guard raw.count > 3 else { return raw }
        return raw.prefix(3) + "-" + raw.dropFirst(3)
    }

    // MARK: Trake

    private func statusLine(_ text: String, showsProgress: Bool) -> some View {
        HStack(spacing: DS.Space.s) {
            if showsProgress { ProgressView() }
            Text(text)
                .font(.dsBody)
                .foregroundStyle(DS.inkMuted)
        }
        .frame(maxWidth: .infinity)
    }

    private func noticeLine(_ text: String) -> some View {
        Text(text)
            .font(.dsBody)
            .foregroundStyle(DS.ink)
            .multilineTextAlignment(.center)
            .padding(DS.Space.m)
            .frame(maxWidth: .infinity)
            .background(DS.fill, in: RoundedRectangle(cornerRadius: DS.Radius.m, style: .continuous))
    }
}

// MARK: - Tekstovi obaveštenja

enum OnlineNoticeText {
    /// `codeTaken` mašina ne emituje (tiho se rešava), ali tekst postoji da
    /// `switch` bude iscrpan.
    @MainActor
    static func text(for notice: OnlineNotice) -> String {
        switch notice {
        case .offline:        return Loc("Prijatelj trenutno nije dostupan. Neka otvori „Igraj preko interneta“.")
        case .busy:           return Loc("Prijatelj je već u partiji.")
        case .selfCode:       return Loc("To je tvoj kod.")
        case .rateLimited:    return Loc("Previše poziva, pokušaj za minut.")
        case .badCode:        return Loc("Neispravan kod.")
        case .declined:       return Loc("Prijatelj je odbio poziv.")
        case .inviteCancelled: return Loc("Poziv je otkazan.")
        case .drawDeclined:   return Loc("Prijatelj je odbio remi.")
        case .codeTaken:      return ""
        case .needsUpdate:    return ""   // tekst dolazi u Task 7
        }
    }
}
