import SwiftUI

struct Timeline: Identifiable, Codable, Hashable {
    let id: UUID
    var title: String
    var tone: TimelineTone
    let createdAt: Date

    init(id: UUID = UUID(), title: String, tone: TimelineTone, createdAt: Date = Date()) {
        self.id = id
        self.title = title
        self.tone = tone
        self.createdAt = createdAt
    }

    func validated() throws -> Timeline {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw ValidationError.emptyTitle }
        guard trimmed.count <= 30 else { throw ValidationError.titleTooLong }
        var copy = self
        copy.title = trimmed
        return copy
    }

    enum ValidationError: LocalizedError {
        case emptyTitle
        case titleTooLong

        var errorDescription: String? {
            switch self {
            case .emptyTitle:
                "请输入时间线名称"
            case .titleTooLong:
                "名称最多 30 个字符"
            }
        }
    }
}

enum TimelineTone: String, Codable, CaseIterable, Identifiable {
    case sage
    case ocean
    case terracotta
    case lavender
    case gold
    case rose

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sage: "鼠尾草绿"
        case .ocean: "雾霾蓝"
        case .terracotta: "陶土橙"
        case .lavender: "雾紫"
        case .gold: "暖金"
        case .rose: "烟粉"
        }
    }

    var color: Color {
        switch self {
        case .sage: Color(red: 0.38, green: 0.55, blue: 0.47)
        case .ocean: Color(red: 0.38, green: 0.51, blue: 0.62)
        case .terracotta: Color(red: 0.70, green: 0.43, blue: 0.32)
        case .lavender: Color(red: 0.57, green: 0.49, blue: 0.66)
        case .gold: Color(red: 0.69, green: 0.55, blue: 0.30)
        case .rose: Color(red: 0.69, green: 0.46, blue: 0.48)
        }
    }
}