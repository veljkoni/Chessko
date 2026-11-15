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

    var onOpen: (() -> Void)?
    var onMessage: ((ServerMessage) -> Void)?
    /// Tačno jednom po soketu, osim posle `close()` (tiho gašenje).
    var onClose: (() -> Void)?

    private var session: URLSession?
    private var task: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?
    private var pingTask: Task<Void, Never>?
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
    func close() {
        finished = true
        onClose = nil
        onOpen = nil
        onMessage = nil
        receiveTask?.cancel()
        pingTask?.cancel()
        receiveTask = nil
        pingTask = nil
        if pendingSends == 0 {
            hardClose(.normalClosure)
        } else {
            Task { [weak self] in
                try? await Task.sleep(for: Self.flushCap)
                self?.hardClose(.normalClosure)
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
                task.sendPing { [weak self] error in
                    if error != nil { Task { @MainActor in self?.fail() } }
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
        hardClose(.goingAway)
        receiveTask = nil
        pingTask = nil
        onOpen = nil
        onMessage = nil
    }
}
