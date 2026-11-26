import SwiftUI

struct TimelineEvent: Identifiable, Hashable {
    let id: String
    var title: String
    var body: String
    var occurredAt: Date
    let createdAt: Date
    var updatedAt: Date

    init(
        id: String = UUID().uuidString.lowercased(),
        title: String,
        body: String = "",
        occurredAt: Date = Date(),
        createdAt: Date = Date(),
        updatedAt: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.body = body
        self.occurredAt = occurredAt
        self.createdAt = createdAt
        self.updatedAt = updatedAt ?? createdAt
    }

    func validated() throws -> TimelineEvent {
        let normalizedID = id.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !normalizedID.isEmpty else { throw MomentLineValidationError.invalidID }
        guard !normalizedTitle.isEmpty else { throw MomentLineValidationError.emptyEventTitle }
        guard normalizedTitle.count <= 100 else { throw MomentLineValidationError.eventTitleTooLong }
        guard body.count <= 5000 else { throw MomentLineValidationError.eventBodyTooLong }
        guard updatedAt >= createdAt else { throw MomentLineValidationError.invalidUpdateTime }
        return TimelineEvent(
            id: normalizedID,
            title: normalizedTitle,
            body: body,
            occurredAt: occurredAt,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

struct Timeline: Identifiable, Hashable {
    let id: String
    var title: String
    var tone: String
    let createdAt: Date
    var events: [TimelineEvent]

    init(
        id: String = UUID().uuidString.lowercased(),
        title: String,
        tone: String,
        createdAt: Date = Date(),
        events: [TimelineEvent] = []
    ) {
        self.id = id
        self.title = title
        self.tone = tone
        self.createdAt = createdAt
        self.events = events
    }

    func validated() throws -> Timeline {
        let normalizedID = id.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedTone = tone.uppercased()

        guard !normalizedID.isEmpty else { throw MomentLineValidationError.invalidID }
        guard !normalizedTitle.isEmpty else { throw MomentLineValidationError.emptyTimelineTitle }
        guard normalizedTitle.count <= 80 else { throw MomentLineValidationError.timelineTitleTooLong }
        guard normalizedTone.range(of: "^#[0-9A-F]{6}$", options: .regularExpression) != nil else {
            throw MomentLineValidationError.invalidTone
        }

        var identifiers = Set<String>()
        let validatedEvents = try events.map { event in
            let validated = try event.validated()
            guard identifiers.insert(validated.id).inserted else {
                throw MomentLineValidationError.duplicateEventID
            }
            return validated
        }
        return Timeline(
            id: normalizedID,
            title: normalizedTitle,
            tone: normalizedTone,
            createdAt: createdAt,
            events: validatedEvents
        )
    }

    var color: Color {
        Color(hex: tone) ?? TimelineTone.sage.color
    }
}

enum MomentLineValidationError: LocalizedError {
    case invalidID
    case emptyTimelineTitle
    case timelineTitleTooLong
    case invalidTone
    case emptyEventTitle
    case eventTitleTooLong
    case eventBodyTooLong
    case invalidUpdateTime
    case duplicateEventID

    var errorDescription: String? {
        switch self {
        case .invalidID:
            "标识无效或缺失"
        case .emptyTimelineTitle:
            "请输入时间线名称"
        case .timelineTitleTooLong:
            "名称最多 80 个字符"
        case .invalidTone:
            "请选择有效的主题颜色"
        case .emptyEventTitle:
            "标题不能为空"
        case .eventTitleTooLong:
            "标题最多 100 个字符"
        case .eventBodyTooLong:
            "备注最多 5000 个字符"
        case .invalidUpdateTime:
            "更新时间不能早于创建时间"
        case .duplicateEventID:
            "事件标识在所属时间线内重复"
        }
    }
}

enum TimelineTone: String, CaseIterable, Identifiable {
    case sage = "#5B7C6E"
    case ocean = "#517A9A"
    case lavender = "#8A6D9B"
    case terracotta = "#B06B5D"
    case gold = "#B18445"
    case slate = "#63758A"
    case olive = "#7B844F"
    case rose = "#A55D78"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sage: "鼠尾草绿"
        case .ocean: "静谧蓝"
        case .lavender: "雾紫"
        case .terracotta: "陶土红"
        case .gold: "暖金"
        case .slate: "石板蓝"
        case .olive: "橄榄绿"
        case .rose: "烟粉"
        }
    }

    var color: Color {
        Color(hex: rawValue) ?? .gray
    }
}

extension Color {
    init?(hex: String) {
        guard hex.count == 7,
              hex.first == "#",
              let value = UInt64(hex.dropFirst(), radix: 16) else {
            return nil
        }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255
        )
    }
}