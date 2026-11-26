import Combine
import Foundation

@MainActor
final class TimelineStore: ObservableObject {
    @Published private(set) var timelines: [Timeline] = []
    @Published private(set) var loadError: String?
    @Published private(set) var eventOrders: [String: EventOrder] = [:]
    @Published var notice: String?

    @Published var timelineOrder: TimelineOrder = .createdDesc {
        didSet { defaults.set(timelineOrder.rawValue, forKey: Keys.timelineOrder) }
    }
    @Published var appearance: AppAppearance = .system {
        didSet { defaults.set(appearance.rawValue, forKey: Keys.appearance) }
    }

    private let fileURL: URL
    private let fileManager: FileManager
    private let defaults: UserDefaults
    private let backupService = BackupService()

    private enum Keys {
        static let timelineOrder = "display.timelineOrder"
        static let eventOrders = "display.eventOrders"
        static let appearance = "display.appearance"
    }

    convenience init() {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        self.init(
            fileURL: directory
                .appendingPathComponent("MomentLine", isDirectory: true)
                .appendingPathComponent("timelines.json")
        )
    }

    init(
        fileURL: URL,
        fileManager: FileManager = .default,
        defaults: UserDefaults = .standard
    ) {
        self.fileURL = fileURL
        self.fileManager = fileManager
        self.defaults = defaults

        if let rawValue = defaults.string(forKey: Keys.timelineOrder),
           let savedOrder = TimelineOrder(rawValue: rawValue) {
            timelineOrder = savedOrder
        }
        if let rawValue = defaults.string(forKey: Keys.appearance),
           let savedAppearance = AppAppearance(rawValue: rawValue) {
            appearance = savedAppearance
        }
        if let savedOrders = defaults.dictionary(forKey: Keys.eventOrders) as? [String: String] {
            eventOrders = savedOrders.compactMapValues(EventOrder.init(rawValue:))
        }
        load()
    }

    var orderedTimelines: [Timeline] {
        sortTimelines(timelines, order: timelineOrder)
    }

    var totalEventCount: Int {
        timelines.reduce(0) { $0 + $1.events.count }
    }

    func timeline(withID id: String) -> Timeline? {
        timelines.first { $0.id == id }
    }

    func eventOrder(for timelineID: String) -> EventOrder {
        eventOrders[timelineID] ?? .newestFirst
    }

    func setEventOrder(_ order: EventOrder, for timelineID: String) {
        eventOrders[timelineID] = order
        defaults.set(
            eventOrders.mapValues(\.rawValue),
            forKey: Keys.eventOrders
        )
    }

    func load() {
        guard fileManager.fileExists(atPath: fileURL.path) else {
            timelines = []
            loadError = nil
            return
        }
        do {
            let data = try Data(contentsOf: fileURL)
            timelines = try backupService.decodeLocal(data)
            loadError = nil
        } catch {
            recordFailure(operation: "read", stage: "decode", error: error)
            loadError = "暂时无法读取时间线或事件，请重试；原有数据未被清除"
        }
    }

    @discardableResult
    func addTimeline(title: String, tone: String) throws -> Timeline {
        try ensureReadable()
        let timeline = try Timeline(title: title, tone: tone).validated()
        var updated = timelines
        updated.append(timeline)
        try commit(updated, operation: "addTimeline")
        notice = "时间线已创建"
        return timeline
    }

    func updateTimeline(id: String, title: String, tone: String) throws {
        try ensureReadable()
        guard let index = timelines.firstIndex(where: { $0.id == id }) else {
            throw TimelineStoreError.missingTimeline
        }
        let existing = timelines[index]
        let updatedTimeline = try Timeline(
            id: existing.id,
            title: title,
            tone: tone,
            createdAt: existing.createdAt,
            events: existing.events
        ).validated()
        guard updatedTimeline != existing else { return }

        var updated = timelines
        updated[index] = updatedTimeline
        try commit(updated, operation: "updateTimeline")
        notice = "时间线已保存"
    }

    func deleteTimeline(id: String) throws {
        try ensureReadable()
        guard timelines.contains(where: { $0.id == id }) else {
            throw TimelineStoreError.missingTimeline
        }
        let updated = timelines.filter { $0.id != id }
        try commit(updated, operation: "deleteTimeline")
        notice = "时间线及其事件已删除"
    }

    @discardableResult
    func addEvent(
        timelineID: String,
        title: String,
        body: String,
        occurredAt: Date
    ) throws -> TimelineEvent {
        try ensureReadable()
        guard let timelineIndex = timelines.firstIndex(where: { $0.id == timelineID }) else {
            throw TimelineStoreError.missingTimeline
        }
        let now = Date()
        let event = try TimelineEvent(
            title: title,
            body: body,
            occurredAt: occurredAt,
            createdAt: now,
            updatedAt: now
        ).validated()
        var updated = timelines
        updated[timelineIndex].events.append(event)
        try commit(updated, operation: "addEvent")
        notice = "事件已创建"
        return event
    }

    func updateEvent(
        timelineID: String,
        eventID: String,
        title: String,
        body: String,
        occurredAt: Date
    ) throws {
        try ensureReadable()
        guard let timelineIndex = timelines.firstIndex(where: { $0.id == timelineID }) else {
            throw TimelineStoreError.missingTimeline
        }
        guard let eventIndex = timelines[timelineIndex].events.firstIndex(where: { $0.id == eventID }) else {
            throw TimelineStoreError.missingEvent
        }
        let existing = timelines[timelineIndex].events[eventIndex]
        var candidate = try TimelineEvent(
            id: existing.id,
            title: title,
            body: body,
            occurredAt: occurredAt,
            createdAt: existing.createdAt,
            updatedAt: existing.updatedAt
        ).validated()
        if candidate.title == existing.title,
           candidate.body == existing.body,
           candidate.occurredAt == existing.occurredAt {
            return
        }
        candidate.updatedAt = max(Date(), candidate.createdAt)

        var updated = timelines
        updated[timelineIndex].events[eventIndex] = candidate
        try commit(updated, operation: "updateEvent")
        notice = "事件已保存"
    }

    func deleteEvent(timelineID: String, eventID: String) throws {
        try ensureReadable()
        guard let timelineIndex = timelines.firstIndex(where: { $0.id == timelineID }) else {
            throw TimelineStoreError.missingTimeline
        }
        guard timelines[timelineIndex].events.contains(where: { $0.id == eventID }) else {
            throw TimelineStoreError.missingEvent
        }
        var updated = timelines
        updated[timelineIndex].events.removeAll { $0.id == eventID }
        try commit(updated, operation: "deleteEvent")
        notice = "事件已删除"
    }

    func makeBackupData() throws -> Data {
        try ensureReadable()
        do {
            return try backupService.encodeBackup(timelines: timelines)
        } catch {
            recordFailure(operation: "export", stage: "encode", error: error)
            throw error
        }
    }

    func prepareImport(data: Data) throws -> ImportPreview {
        try ensureReadable()
        do {
            return try backupService.preview(data: data, localTimelines: timelines)
        } catch {
            recordFailure(operation: "import", stage: "validate", error: error)
            throw error
        }
    }

    @discardableResult
    func applyImport(_ preview: ImportPreview, resolution: ConflictResolution?) throws -> ImportResult {
        try ensureReadable()
        let merged = try backupService.merge(
            localTimelines: timelines,
            preview: preview,
            resolution: resolution
        )
        let result = backupService.result(for: preview, resolution: resolution)
        let actualChanges = result.timelines.added + result.timelines.replaced
            + result.events.added + result.events.replaced
        if actualChanges > 0 {
            try commit(merged, operation: "import")
        }
        return result
    }

    func clearNotice() {
        notice = nil
    }

    func recordFailure(operation: String, stage: String, error: Error) {
        let record: [String: Any] = [
            "operation": operation,
            "occurredAt": ISODateCodec.string(from: Date()),
            "stage": stage,
            "reason": String(describing: type(of: error))
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]) else {
            return
        }
        let logURL = fileURL.deletingLastPathComponent().appendingPathComponent("diagnostics.jsonl")
        do {
            try fileManager.createDirectory(
                at: logURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            var line = data
            line.append(0x0A)
            if fileManager.fileExists(atPath: logURL.path) {
                let handle = try FileHandle(forWritingTo: logURL)
                try handle.seekToEnd()
                try handle.write(contentsOf: line)
                try handle.close()
            } else {
                try line.write(to: logURL, options: .atomic)
            }
        } catch {
            // 诊断写入失败不影响用户数据及原始错误反馈。
        }
    }

    private func ensureReadable() throws {
        guard loadError == nil else { throw TimelineStoreError.readFailed }
    }

    private func commit(_ updated: [Timeline], operation: String) throws {
        do {
            let directory = fileURL.deletingLastPathComponent()
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            let data = try backupService.encodeLocal(updated)
            let normalized = try backupService.decodeLocal(data)
            try data.write(to: fileURL, options: .atomic)
            timelines = normalized
            loadError = nil
        } catch {
            recordFailure(operation: operation, stage: "atomicWrite", error: error)
            throw TimelineStoreError.saveFailed
        }
    }
}

enum TimelineStoreError: LocalizedError {
    case readFailed
    case saveFailed
    case missingTimeline
    case missingEvent

    var errorDescription: String? {
        switch self {
        case .readFailed:
            "本地数据暂不可用，请重试读取后再保存"
        case .saveFailed:
            "暂时无法保存，请检查设备存储空间后重试；原有数据保持不变"
        case .missingTimeline:
            "所属时间线已删除，本次事件修改未保存"
        case .missingEvent:
            "该事件已不存在，本次修改未保存"
        }
    }
}