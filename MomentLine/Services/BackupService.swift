import Foundation

struct MomentBackup {
    static let format = "MomentLine"
    static let currentVersion = 2
}

struct ImportClassStats: Hashable {
    var added = 0
    var duplicates = 0
    var conflicts = 0
    var invalid = 0
}

struct ImportedEvent: Hashable {
    let timelineID: String
    let event: TimelineEvent
}

struct ImportPreview: Identifiable {
    let id = UUID()
    var timelineStats = ImportClassStats()
    var eventStats = ImportClassStats()
    var timelineAdditions: [Timeline] = []
    var timelineDuplicates: [Timeline] = []
    var timelineConflicts: [Timeline] = []
    var eventAdditions: [ImportedEvent] = []
    var eventDuplicates: [ImportedEvent] = []
    var eventConflicts: [ImportedEvent] = []

    var hasConflicts: Bool {
        timelineStats.conflicts + eventStats.conflicts > 0
    }
}

enum ConflictResolution: String, CaseIterable, Identifiable {
    case keepLocal
    case useBackup

    var id: String { rawValue }

    var title: String {
        switch self {
        case .keepLocal: "保留本地"
        case .useBackup: "使用备份"
        }
    }

    var detail: String {
        switch self {
        case .keepLocal:
            "所有冲突记录保留本地内容，备份中的冲突版本将被跳过；新增记录仍会导入。"
        case .useBackup:
            "使用备份替换全部冲突的时间线字段和事件内容；本地独有的时间线与事件不会删除。"
        }
    }
}

struct ImportCounts: Hashable {
    let added: Int
    let skipped: Int
    let replaced: Int
}

struct ImportResult: Hashable {
    let timelines: ImportCounts
    let events: ImportCounts
}

enum BackupError: LocalizedError {
    case invalidFile
    case invalidLocal
    case unsupportedVersion(Int)
    case invalidRecord(location: String, reason: String)
    case conflictResolutionRequired
    case exportFailed

    var errorDescription: String? {
        switch self {
        case .invalidFile:
            "这不是有效的 Moment Line 备份文件"
        case .invalidLocal:
            "本地时间线格式不完整"
        case .unsupportedVersion:
            "该备份版本暂不受支持，请更新应用或使用兼容备份"
        case let .invalidRecord(location, reason):
            "\(location)有误：\(reason)"
        case .conflictResolutionRequired:
            "请选择冲突处理方式"
        case .exportFailed:
            "备份文件未能生成，请检查存储空间后重试"
        }
    }
}

struct BackupService {
    func encodeBackup(timelines: [Timeline], exportedAt: Date = Date()) throws -> Data {
        let root: [String: Any] = [
            "format": MomentBackup.format,
            "version": MomentBackup.currentVersion,
            "exportedAt": ISODateCodec.string(from: exportedAt),
            "timelines": try timelines.map(timelineDictionary)
        ]
        return try encode(root)
    }

    func encodeLocal(_ timelines: [Timeline]) throws -> Data {
        try encode(["timelines": try timelines.map(timelineDictionary)], prettyPrinted: false)
    }

    func decodeBackup(_ data: Data) throws -> [Timeline] {
        guard String(data: data, encoding: .utf8) != nil else {
            throw BackupError.invalidFile
        }

        let root: [String: Any]
        do {
            guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw BackupError.invalidFile
            }
            root = object
        } catch let error as BackupError {
            throw error
        } catch {
            throw BackupError.invalidFile
        }

        guard root["format"] as? String == MomentBackup.format,
              let versionValue = root["version"], !(versionValue is Bool),
              let version = versionValue as? Int,
              let exportedText = root["exportedAt"] as? String,
              ISODateCodec.date(from: exportedText) != nil,
              let records = root["timelines"] as? [Any] else {
            throw BackupError.invalidFile
        }
        guard version == MomentBackup.currentVersion else {
            throw BackupError.unsupportedVersion(version)
        }
        return try decodeTimelines(records)
    }

    func decodeLocal(_ data: Data) throws -> [Timeline] {
        guard String(data: data, encoding: .utf8) != nil else {
            throw BackupError.invalidLocal
        }
        do {
            guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let records = root["timelines"] as? [Any] else {
                throw BackupError.invalidLocal
            }
            return try decodeTimelines(records)
        } catch let error as BackupError {
            throw error
        } catch {
            throw BackupError.invalidLocal
        }
    }

    func preview(data: Data, localTimelines: [Timeline]) throws -> ImportPreview {
        let imported = try decodeBackup(data)
        let localByID = Dictionary(uniqueKeysWithValues: localTimelines.map { ($0.id, $0) })
        var preview = ImportPreview()

        for timeline in imported {
            let localTimeline = localByID[timeline.id]
            if localTimeline == nil {
                preview.timelineStats.added += 1
                preview.timelineAdditions.append(timeline)
            } else if sameTimelineFields(localTimeline!, timeline) {
                preview.timelineStats.duplicates += 1
                preview.timelineDuplicates.append(timeline)
            } else {
                preview.timelineStats.conflicts += 1
                preview.timelineConflicts.append(timeline)
            }

            let localEvents = Dictionary(uniqueKeysWithValues: (localTimeline?.events ?? []).map { ($0.id, $0) })
            for event in timeline.events {
                let item = ImportedEvent(timelineID: timeline.id, event: event)
                guard let localEvent = localEvents[event.id] else {
                    preview.eventStats.added += 1
                    preview.eventAdditions.append(item)
                    continue
                }
                if sameEvent(localEvent, event) {
                    preview.eventStats.duplicates += 1
                    preview.eventDuplicates.append(item)
                } else {
                    preview.eventStats.conflicts += 1
                    preview.eventConflicts.append(item)
                }
            }
        }
        return preview
    }

    func merge(
        localTimelines: [Timeline],
        preview: ImportPreview,
        resolution: ConflictResolution?
    ) throws -> [Timeline] {
        guard preview.timelineStats.invalid == 0, preview.eventStats.invalid == 0 else {
            throw BackupError.invalidFile
        }
        if preview.hasConflicts && resolution == nil {
            throw BackupError.conflictResolutionRequired
        }

        var result = localTimelines.map { timeline in
            Timeline(
                id: timeline.id,
                title: timeline.title,
                tone: timeline.tone,
                createdAt: timeline.createdAt,
                events: timeline.events
            )
        }

        if resolution == .useBackup {
            for imported in preview.timelineConflicts {
                guard let index = result.firstIndex(where: { $0.id == imported.id }) else { continue }
                result[index] = Timeline(
                    id: result[index].id,
                    title: imported.title,
                    tone: imported.tone,
                    createdAt: imported.createdAt,
                    events: result[index].events
                )
            }
            for item in preview.eventConflicts {
                guard let timelineIndex = result.firstIndex(where: { $0.id == item.timelineID }),
                      let eventIndex = result[timelineIndex].events.firstIndex(where: { $0.id == item.event.id }) else {
                    continue
                }
                result[timelineIndex].events[eventIndex] = item.event
            }
        }

        for item in preview.eventAdditions {
            guard let timelineIndex = result.firstIndex(where: { $0.id == item.timelineID }) else { continue }
            result[timelineIndex].events.append(item.event)
        }
        for imported in preview.timelineAdditions {
            result.append(imported)
        }
        return result
    }

    func result(for preview: ImportPreview, resolution: ConflictResolution?) -> ImportResult {
        func counts(_ stats: ImportClassStats) -> ImportCounts {
            let replaced = resolution == .useBackup ? stats.conflicts : 0
            let kept = resolution == .keepLocal ? stats.conflicts : 0
            return ImportCounts(
                added: stats.added,
                skipped: stats.duplicates + kept,
                replaced: replaced
            )
        }
        return ImportResult(timelines: counts(preview.timelineStats), events: counts(preview.eventStats))
    }

    private func encode(_ root: [String: Any], prettyPrinted: Bool = true) throws -> Data {
        do {
            var options: JSONSerialization.WritingOptions = [.sortedKeys]
            if prettyPrinted { options.insert(.prettyPrinted) }
            return try JSONSerialization.data(withJSONObject: root, options: options)
        } catch let error as BackupError {
            throw error
        } catch {
            throw BackupError.exportFailed
        }
    }

    private func decodeTimelines(_ records: [Any]) throws -> [Timeline] {
        var identifiers = Set<String>()
        var timelines: [Timeline] = []
        for (index, record) in records.enumerated() {
            let timeline = try decodeTimeline(record, index: index)
            guard identifiers.insert(timeline.id).inserted else {
                throw BackupError.invalidRecord(
                    location: "第 \(index + 1) 条时间线",
                    reason: "id 在文件中重复"
                )
            }
            timelines.append(timeline)
        }
        return timelines
    }

    private func decodeTimeline(_ value: Any, index: Int) throws -> Timeline {
        let location = "第 \(index + 1) 条时间线"
        guard let record = value as? [String: Any] else {
            throw BackupError.invalidRecord(location: location, reason: "记录必须是 JSON 对象")
        }
        guard let id = record["id"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "id 缺失或不是字符串")
        }
        guard let title = record["title"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "title 缺失或不是字符串")
        }
        guard let tone = record["tone"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "tone 缺失或不是字符串")
        }
        guard let createdText = record["createdAt"] as? String,
              let createdAt = ISODateCodec.date(from: createdText) else {
            throw BackupError.invalidRecord(location: location, reason: "createdAt 无效或缺少时区")
        }
        guard let eventRecords = record["events"] as? [Any] else {
            throw BackupError.invalidRecord(location: location, reason: "events 集合缺失或格式错误")
        }

        var eventIdentifiers = Set<String>()
        var events: [TimelineEvent] = []
        for (eventIndex, eventRecord) in eventRecords.enumerated() {
            let event = try decodeEvent(eventRecord, timelineIndex: index, eventIndex: eventIndex)
            guard eventIdentifiers.insert(event.id).inserted else {
                throw BackupError.invalidRecord(
                    location: "第 \(index + 1) 条时间线的第 \(eventIndex + 1) 条事件",
                    reason: "id 在时间线内重复"
                )
            }
            events.append(event)
        }

        do {
            return try Timeline(id: id, title: title, tone: tone, createdAt: createdAt, events: events).validated()
        } catch {
            throw BackupError.invalidRecord(location: location, reason: error.localizedDescription)
        }
    }

    private func decodeEvent(_ value: Any, timelineIndex: Int, eventIndex: Int) throws -> TimelineEvent {
        let location = "第 \(timelineIndex + 1) 条时间线的第 \(eventIndex + 1) 条事件"
        guard let record = value as? [String: Any] else {
            throw BackupError.invalidRecord(location: location, reason: "记录必须是 JSON 对象")
        }
        guard let id = record["id"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "id 缺失或不是字符串")
        }
        guard let title = record["title"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "title 缺失或不是字符串")
        }
        guard let body = record["body"] as? String else {
            throw BackupError.invalidRecord(location: location, reason: "body 缺失或不是字符串")
        }
        guard let occurredText = record["occurredAt"] as? String,
              let occurredAt = ISODateCodec.date(from: occurredText) else {
            throw BackupError.invalidRecord(location: location, reason: "occurredAt 无效或缺少时区")
        }
        guard let createdText = record["createdAt"] as? String,
              let createdAt = ISODateCodec.date(from: createdText) else {
            throw BackupError.invalidRecord(location: location, reason: "createdAt 无效或缺少时区")
        }
        guard let updatedText = record["updatedAt"] as? String,
              let updatedAt = ISODateCodec.date(from: updatedText) else {
            throw BackupError.invalidRecord(location: location, reason: "updatedAt 无效或缺少时区")
        }

        do {
            return try TimelineEvent(
                id: id,
                title: title,
                body: body,
                occurredAt: occurredAt,
                createdAt: createdAt,
                updatedAt: updatedAt
            ).validated()
        } catch {
            throw BackupError.invalidRecord(location: location, reason: error.localizedDescription)
        }
    }

    private func timelineDictionary(_ timeline: Timeline) throws -> [String: Any] {
        let value = try timeline.validated()
        return [
            "id": value.id,
            "title": value.title,
            "tone": value.tone,
            "createdAt": ISODateCodec.string(from: value.createdAt),
            "events": try value.events.map(eventDictionary)
        ]
    }

    private func eventDictionary(_ event: TimelineEvent) throws -> [String: Any] {
        let value = try event.validated()
        return [
            "id": value.id,
            "title": value.title,
            "body": value.body,
            "occurredAt": ISODateCodec.string(from: value.occurredAt),
            "createdAt": ISODateCodec.string(from: value.createdAt),
            "updatedAt": ISODateCodec.string(from: value.updatedAt)
        ]
    }

    private func sameTimelineFields(_ left: Timeline, _ right: Timeline) -> Bool {
        left.id == right.id
            && left.title == right.title
            && left.tone == right.tone
            && left.createdAt == right.createdAt
    }

    private func sameEvent(_ left: TimelineEvent, _ right: TimelineEvent) -> Bool {
        left.id == right.id
            && left.title == right.title
            && left.body == right.body
            && left.occurredAt == right.occurredAt
            && left.createdAt == right.createdAt
            && left.updatedAt == right.updatedAt
    }
}

func compareCodePoints(_ left: String, _ right: String) -> Int {
    let leftScalars = Array(left.unicodeScalars.map(\.value))
    let rightScalars = Array(right.unicodeScalars.map(\.value))
    for (leftValue, rightValue) in zip(leftScalars, rightScalars) {
        if leftValue != rightValue {
            return leftValue < rightValue ? -1 : 1
        }
    }
    if leftScalars.count == rightScalars.count { return 0 }
    return leftScalars.count < rightScalars.count ? -1 : 1
}

func sortTimelines(_ timelines: [Timeline], order: TimelineOrder) -> [Timeline] {
    timelines.sorted { left, right in
        switch order {
        case .titleAsc, .titleDesc:
            let titleDifference = compareCodePoints(left.title.lowercased(), right.title.lowercased())
            if titleDifference != 0 {
                return order == .titleAsc ? titleDifference < 0 : titleDifference > 0
            }
            if left.createdAt != right.createdAt {
                return left.createdAt > right.createdAt
            }
            return compareCodePoints(left.id, right.id) < 0
        case .createdAsc, .createdDesc:
            if left.createdAt != right.createdAt {
                return order == .createdAsc ? left.createdAt < right.createdAt : left.createdAt > right.createdAt
            }
            return compareCodePoints(left.id, right.id) < 0
        }
    }
}

func sortEvents(_ events: [TimelineEvent], order: EventOrder) -> [TimelineEvent] {
    events.sorted { left, right in
        if left.occurredAt != right.occurredAt {
            return order == .newestFirst
                ? left.occurredAt > right.occurredAt
                : left.occurredAt < right.occurredAt
        }
        if left.updatedAt != right.updatedAt {
            return left.updatedAt > right.updatedAt
        }
        return compareCodePoints(left.id, right.id) < 0
    }
}

enum ISODateCodec {
    private static let pattern = #"^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(Z|[+-]\d{2}:\d{2})$"#

    static func string(from date: Date) -> String {
        makeFormatter(fractionalSeconds: true).string(from: date)
    }

    static func date(from string: String) -> Date? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let fullRange = NSRange(string.startIndex..<string.endIndex, in: string)
        guard let match = regex.firstMatch(in: string, range: fullRange),
              match.range.location == 0,
              match.range.length == fullRange.length,
              let year = integerCapture(1, in: string, match: match),
              let month = integerCapture(2, in: string, match: match),
              let day = integerCapture(3, in: string, match: match),
              let hour = integerCapture(4, in: string, match: match),
              let minute = integerCapture(5, in: string, match: match),
              let second = integerCapture(6, in: string, match: match),
              let offset = textCapture(8, in: string, match: match) else {
            return nil
        }

        let leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        let days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 30, 31, 30, 31, 30, 31]
        guard month >= 1, month <= 12,
              day >= 1, day <= days[month - 1],
              hour <= 23, minute <= 59, second <= 59 else {
            return nil
        }

        if offset != "Z" {
            let offsetText = offset as NSString
            guard let offsetHour = Int(offsetText.substring(with: NSRange(location: 1, length: 2))),
                  let offsetMinute = Int(offsetText.substring(with: NSRange(location: 4, length: 2))),
                  offsetHour <= 14,
                  offsetMinute <= 59,
                  offsetHour != 14 || offsetMinute == 0 else {
                return nil
            }
        }

        var normalized = string
        let fractionRange = match.range(at: 7)
        if fractionRange.location != NSNotFound, let range = Range(fractionRange, in: normalized) {
            let fraction = String(normalized[range])
            normalized.replaceSubrange(
                range,
                with: fraction.padding(toLength: 6, withPad: "0", startingAt: 0)
            )
            return makeFormatter(fractionalSeconds: true).date(from: normalized)
        }
        return makeFormatter(fractionalSeconds: false).date(from: normalized)
    }

    private static func integerCapture(
        _ index: Int,
        in string: String,
        match: NSTextCheckingResult
    ) -> Int? {
        guard let text = textCapture(index, in: string, match: match) else { return nil }
        return Int(text)
    }

    private static func textCapture(
        _ index: Int,
        in string: String,
        match: NSTextCheckingResult
    ) -> String? {
        let range = match.range(at: index)
        guard range.location != NSNotFound, let swiftRange = Range(range, in: string) else { return nil }
        return String(string[swiftRange])
    }

    private static func makeFormatter(fractionalSeconds: Bool) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.isLenient = false
        formatter.dateFormat = fractionalSeconds
            ? "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXXXX"
            : "yyyy-MM-dd'T'HH:mm:ssXXXXX"
        return formatter
    }
}