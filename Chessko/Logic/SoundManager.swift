import AVFoundation

// MARK: - Sound Manager
//
// Plays chess piece sounds from bundled MP3 files (move-self.mp3, capture.mp3).
// Uses .playback + mixWithOthers — plays even when the mute switch is on,
// without interrupting background music.

final class SoundManager: @unchecked Sendable {

    static let shared = SoundManager()

    private var movePlayer:    AVAudioPlayer?
    private var capturePlayer: AVAudioPlayer?

    private init() {
        UserDefaults.standard.register(defaults: ["soundEnabled": true])

        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: .mixWithOthers)
        try? AVAudioSession.sharedInstance().setActive(true)

        if let url = Bundle.main.url(forResource: "move-self", withExtension: "mp3") {
            movePlayer = try? AVAudioPlayer(contentsOf: url)
            movePlayer?.prepareToPlay()
        }
        if let url = Bundle.main.url(forResource: "capture", withExtension: "mp3") {
            capturePlayer = try? AVAudioPlayer(contentsOf: url)
            capturePlayer?.prepareToPlay()
        }
    }

    var isSoundEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "soundEnabled") }
        set { UserDefaults.standard.set(newValue, forKey: "soundEnabled") }
    }

    func playMove() {
        guard isSoundEnabled else { return }
        movePlayer?.stop()
        movePlayer?.currentTime = 0
        movePlayer?.play()
    }

    func playCapture() {
        guard isSoundEnabled else { return }
        capturePlayer?.stop()
        capturePlayer?.currentTime = 0
        capturePlayer?.play()
    }
}
