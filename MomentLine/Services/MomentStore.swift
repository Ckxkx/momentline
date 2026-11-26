import Combine
import Foundation

@MainActor
final class TimelineStore: ObservableObject {
    @Published private(set) var timelines: [Timeline] = []
    @Published private(set) var loadError: String?

    private let fileURL: URL
    private let fileManager: FileManager

    convenience init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.init(
            fileURL: directory
                .appendingPathComponent("MomentLine", isDirectory: true)
                .appendingPathComponent("timelines.json")
        )
    }

    init(fileURL: URL, fileManager: FileManager = .default) {
        self.fileURL = fileURL
        self.fileManager = fileManager
        load()
    }

    var orderedTimelines: [Timeline] {
        timelines.sorted { left, right in
            if left.createdAt != right.createdAt {
                return left.createdAt > right.createdAt
            }
            return left.id.uuidString < right.id.uuidString
        }
    }

    func load() {
        guard fileManager.fileExists(atPath: fileURL.path) else {
            timelines = []
            loadError = nil
            return
        }
        do {
            let data = try Data(contentsOf: fileURL)
            let decoded = try JSONDecoder().decode([Timeline].self, from: data)
            guard Set(decoded.map(\.id)).count == decoded.count else {
                throw TimelineStoreError.readFailed
            }
            timelines = try decoded.map { try $0.validated() }
            loadError = nil
        } catch {
            loadError = "暂时无法读取时间线，请重试；原有数据未被清除"
        }
    }

    @discardableResult
    func add(title: String, tone: TimelineTone) throws -> Timeline {
        guard loadError == nil else { throw TimelineStoreError.readFailed }
        let timeline = try Timeline(title: title, tone: tone).validated()
        var updated = timelines
        updated.append(timeline)
        try persist(updated)
        timelines = updated
        return timeline
    }

    private func persist(_ updated: [Timeline]) throws {
        do {
            let directory = fileURL.deletingLastPathComponent()
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(updated)
            try data.write(to: fileURL, options: .atomic)
        } catch {
            throw TimelineStoreError.saveFailed
        }
    }
}

enum TimelineStoreError: LocalizedError {
    case readFailed
    case saveFailed

    var errorDescription: String? {
        switch self {
        case .readFailed:
            "本地数据暂不可用，请重试读取后再保存"
        case .saveFailed:
            "暂时无法保存，请检查设备存储空间后重试"
        }
    }
}