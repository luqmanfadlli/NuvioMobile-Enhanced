import Foundation
import UIKit
#if canImport(ActivityKit) && os(iOS) && !targetEnvironment(macCatalyst)
import ActivityKit
#endif

private let downloadsLiveStatusUpdatedNotification = Notification.Name("NuvioDownloadsLiveStatusUpdated")
private let downloadsLiveStatusPayloadKey = "nuvio.downloads.live_status.payload"

final class DownloadsLiveActivityManager {
    static let shared = DownloadsLiveActivityManager()

    private var observer: NSObjectProtocol?

    private init() {}

    func start() {
        guard observer == nil else { return }

        observer = NotificationCenter.default.addObserver(
            forName: downloadsLiveStatusUpdatedNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.syncFromPayloadStore()
        }

        syncFromPayloadStore()
    }

    private func syncFromPayloadStore() {
#if canImport(ActivityKit) && os(iOS) && !targetEnvironment(macCatalyst)
        guard #available(iOS 16.1, *) else { return }

        let payload = loadPayload()

        var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid
        backgroundTaskId = UIApplication.shared.beginBackgroundTask(withName: "DownloadsLiveActivitySync") {
            UIApplication.shared.endBackgroundTask(backgroundTaskId)
            backgroundTaskId = .invalid
        }

        Task {
            await apply(payload)
            if backgroundTaskId != .invalid {
                UIApplication.shared.endBackgroundTask(backgroundTaskId)
                backgroundTaskId = .invalid
            }
        }
#endif
    }

    private func loadPayload() -> DownloadsLiveStatusPayload? {
        guard let encoded = UserDefaults.standard.string(forKey: downloadsLiveStatusPayloadKey) else {
            return nil
        }
        let data = Data(encoded.utf8)
        return try? JSONDecoder().decode(DownloadsLiveStatusPayload.self, from: data)
    }

#if canImport(ActivityKit) && os(iOS) && !targetEnvironment(macCatalyst)
    @available(iOS 16.1, *)
    private func apply(_ payload: DownloadsLiveStatusPayload?) async {
        let existing = Activity<DownloadsLiveActivityAttributes>.activities.first

        guard let payload else {
            if let existing {
                await existing.end(dismissalPolicy: .immediate)
            }
            return
        }

        let state = DownloadsLiveActivityAttributes.ContentState(
            status: payload.status,
            progressPercent: payload.progressPercent,
            transferredText: transferredText(payload)
        )

        if let existing, existing.attributes.downloadId == payload.id {
            await existing.update(using: state)
            return
        }

        if let existing {
            await existing.end(dismissalPolicy: .immediate)
        }

        let attributes = DownloadsLiveActivityAttributes(
            downloadId: payload.id,
            title: payload.title,
            subtitle: payload.subtitle
        )

        _ = try? Activity<DownloadsLiveActivityAttributes>.request(
            attributes: attributes,
            contentState: state,
            pushType: nil
        )
    }
#endif

    private func transferredText(_ payload: DownloadsLiveStatusPayload) -> String {
        let downloaded = formatBytes(payload.downloadedBytes)
        if let total = payload.totalBytes {
            return "\(downloaded) / \(formatBytes(total))"
        }
        return downloaded
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}

#if canImport(ActivityKit) && os(iOS) && !targetEnvironment(macCatalyst)
@available(iOS 16.1, *)
struct DownloadsLiveActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        let status: String
        let progressPercent: Int
        let transferredText: String
    }

    let downloadId: String
    let title: String
    let subtitle: String
}
#endif

private struct DownloadsLiveStatusPayload: Decodable {
    let id: String
    let title: String
    let subtitle: String
    let status: String
    let downloadedBytes: Int64
    let totalBytes: Int64?
    let progressPercent: Int
}
