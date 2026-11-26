import SwiftUI

enum TimelineOrder: String, CaseIterable, Identifiable {
    case createdDesc
    case createdAsc
    case titleAsc
    case titleDesc

    var id: String { rawValue }

    var title: String {
        switch self {
        case .createdDesc: "创建时间倒序"
        case .createdAsc: "创建时间正序"
        case .titleAsc: "名称正序"
        case .titleDesc: "名称倒序"
        }
    }
}

enum EventOrder: String, CaseIterable, Identifiable {
    case newestFirst
    case oldestFirst

    var id: String { rawValue }

    var title: String {
        switch self {
        case .newestFirst: "发生时间倒序"
        case .oldestFirst: "发生时间正序"
        }
    }
}

enum AppAppearance: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: "跟随系统"
        case .light: "浅色"
        case .dark: "深色"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}