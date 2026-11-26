import SwiftUI

@main
struct MomentLineApp: App {
    @StateObject private var store = TimelineStore()

    var body: some Scene {
        WindowGroup {
            TimelineView()
                .environmentObject(store)
                .preferredColorScheme(store.appearance.colorScheme)
                .tint(TimelineTone.sage.color)
        }
    }
}