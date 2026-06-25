import AVFoundation

// MARK: - Sound Manager
//
// Plays chess piece sounds from bundled MP3 files.
// Respects the iOS mute/ringer switch (.ambient category).

final class SoundManager: @unchecked Sendable {

    static let shared = SoundManager()

    private var movePlayer:    AVAudioPlayer?
    private var capturePlayer: AVAudioPlayer?

    private init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
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

    func playMove() {
        movePlayer?.stop()
        movePlayer?.currentTime = 0
        movePlayer?.play()
    }

    func playCapture() {
        capturePlayer?.stop()
        capturePlayer?.currentTime = 0
        capturePlayer?.play()
    }
}
