import Foundation

// Jedan WebSocket ka serveru za igru preko interneta. Jednokratan: kad se
// zatvori ili padne, pravi se nov primerak (OnlineViewModel numeriše sokete).
// Jedino mesto u aplikaciji koje koristi mrežu. Callback-ovi stižu na MainActor.

/// Delegat samo prosleđuje događaje otvaranja/zatvaranja; sve ostalo je na `OnlineConnection`.
private final class OnlineSocketDelegate: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    private let didOpen: @Sendable () -> Void
    private let didEnd: @Sendable () -> Void

    init(didOpen: @escaping @Sendable () -> Void, didEnd: @escaping @Sendable () -> Void) {
        self.didOpen = didOpen
        self.didEnd = didEnd
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                    didOpenWithProtocol protocol: String?) { didOpen() }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                    didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) { didEnd() }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) { didEnd() }
}

@MainActor
final class OnlineConnection {
    static let url = URL(string: "wss://play.chessko.app/ws")!
    private static let pingInterval: Duration = .seconds(25)
    /// Pong koji ne stigne u ovom roku znači polumrtvu vezu (OkHttp na Androidu radi isto):
    /// `sendPing` tada nikad ne pozove callback, pa bez roka igrač čeka zauvek.
    private static let pongTimeout: Duration = .seconds(10)

    var onOpen: (() -> Void)?
    var onMessage: ((ServerMessage) -> Void)?
    /// Tačno jednom po soketu, osim posle `close()` (tiho gašenje).
    var onClose: (() -> Void)?

    private var session: URLSession?
    private var task: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?
    private var pingTask: Task<Void, Never>?
    private var pongDeadline: Task<Void, Never>?
    /// Redni broj pinga koji još čeka pong (`nil` kad nijedan ne čeka).
    private var pendingPing: Int?
    private var pingSeq = 0
    private var finished = false
    /// Poruke poslate a još nepotvrđene upisom u soket; `close()` ih sačeka.
    private var pendingSends = 0

    /// Najduže čekanje da se poslate poruke isprazne pre gašenja (npr. „resign").
    private static let flushCap: Duration = .seconds(2)

    init() {}

    func open() {
        guard task == nil, !finished else { return }
        let delegate = OnlineSocketDelegate(
            didOpen: { [weak self] in Task { @MainActor in self?.handleOpened() } },
            didEnd: { [weak self] in Task { @MainActor in self?.fail() } })
        let session = URLSession(configuration: .ephemeral, delegate: delegate, delegateQueue: nil)
        var request = URLRequest(url: Self.url)
        request.timeoutInterval = 15
        let task = session.webSocketTask(with: request)
        self.session = session
        self.task = task
        task.resume()
        receiveTask = Task { [weak self] in
            while !Task.isCancelled {
                do {
                    let message = try await task.receive()
                    self?.handle(message)
                } catch {
                    self?.fail()
                    return
                }
            }
        }
    }

    func send(_ message: ClientMessage) {
        guard let task, !finished,
              let data = try? JSONEncoder().encode(message),
              let text = String(data: data, encoding: .utf8) else { return }
        pendingSends += 1
        // Jaka referenca namerno: posle `close()` vlasnik pušta objekat, a upis
        // mora da stigne do kraja (callback traje kratko, pa nema ciklusa).
        task.send(.string(text)) { error in
            Task { @MainActor in
                self.pendingSends -= 1
                if error != nil { self.fail() }   // posle close() je `finished`, pa ništa
                self.closeNowIfFlushed()
            }
        }
    }

    /// Tiho gašenje: nijedan callback više ne stiže. Poruke u letu (poslednji
    /// `resign`, `move`) se sačekaju, najviše `flushCap`, pa se soket zatvara
    /// regularno (`normalClosure`).
    /// `completion` stiže kad je soket stvarno zatvoren (posle ispiranja poruka u letu).
    func close(completion: (@MainActor () -> Void)? = nil) {
        finished = true
        onClose = nil
        onOpen = nil
        onMessage = nil
        receiveTask?.cancel()
        pingTask?.cancel()
        pongDeadline?.cancel()
        receiveTask = nil
        pingTask = nil
        pongDeadline = nil
        if pendingSends == 0 {
            hardClose(.normalClosure)
            completion?()
        } else {
            Task { [weak self] in
                try? await Task.sleep(for: Self.flushCap)
                self?.hardClose(.normalClosure)
                completion?()
            }
        }
    }

    private func closeNowIfFlushed() {
        if finished, pendingSends == 0 { hardClose(.normalClosure) }
    }

    private func hardClose(_ code: URLSessionWebSocketTask.CloseCode) {
        guard let task else { return }
        task.cancel(with: code, reason: nil)
        session?.invalidateAndCancel()
        self.task = nil
        session = nil
    }

    // MARK: Interno

    private func handleOpened() {
        guard !finished else { return }
        onOpen?()
        startPing()
    }

    private func handle(_ message: URLSessionWebSocketTask.Message) {
        guard !finished else { return }
        let data: Data?
        switch message {
        case .string(let s): data = s.data(using: .utf8)
        case .data(let d): data = d
        @unknown default: data = nil
        }
        // Neispravan JSON se preskače; protokol nepoznate poruke dekodira u .unknown.
        guard let data, let decoded = try? JSONDecoder().decode(ServerMessage.self, from: data) else { return }
        onMessage?(decoded)
    }

    private func startPing() {
        pingTask?.cancel()
        pingTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: Self.pingInterval)
                guard !Task.isCancelled, let self, let task = self.task, !self.finished else { return }
                self.pingSeq += 1
                let seq = self.pingSeq
                self.pendingPing = seq
                task.sendPing { [weak self] error in
                    Task { @MainActor in
                        guard let self else { return }
                        if error != nil { self.fail(); return }
                        if self.pendingPing == seq { self.pendingPing = nil }
                    }
                }
                self.pongDeadline?.cancel()
                self.pongDeadline = Task { [weak self] in
                    try? await Task.sleep(for: Self.pongTimeout)
                    guard !Task.isCancelled, let self, self.pendingPing == seq else { return }
                    self.fail()   // jedan onClose: `fail` proverava `finished`
                }
            }
        }
    }

    /// Svaka greška/zatvaranje završava ovde; `finished` garantuje jedan `onClose`.
    private func fail() {
        guard !finished else { return }
        finished = true
        teardown()
        onClose?()
    }

    private func teardown() {
        receiveTask?.cancel()
        pingTask?.cancel()
        pongDeadline?.cancel()
        hardClose(.goingAway)
        receiveTask = nil
        pingTask = nil
        pongDeadline = nil
        onOpen = nil
        onMessage = nil
    }
}
