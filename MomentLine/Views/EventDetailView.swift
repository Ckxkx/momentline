import SwiftUI

struct EventDetailView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss
    let timelineID: String
    let eventID: String

    @State private var editingEvent: TimelineEvent?
    @State private var showingDeleteConfirmation = false
    @State private var errorMessage: String?

    private var timeline: Timeline? {
        store.timeline(withID: timelineID)
    }

    private var event: TimelineEvent? {
        timeline?.events.first { $0.id == eventID }
    }

    var body: some View {
        Group {
            if let timeline, let event {
                Form {
                    Section("所属时间线") {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(timeline.color)
                                .frame(width: 14, height: 14)
                            Text(timeline.title)
                                .font(.headline)
                        }
                        .frame(minHeight: 44)
                    }

                    Section("事件") {
                        Text(event.title)
                            .font(.title3.weight(.semibold))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 4)
                        if event.body.isEmpty {
                            Text("无备注")
                                .foregroundStyle(.secondary)
                        } else {
                            Text(event.body)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .multilineTextAlignment(.leading)
                                .textSelection(.enabled)
                                .padding(.vertical, 4)
                        }
                    }

                    Section("时间") {
                        EventDateRow(title: "发生时间", date: event.occurredAt)
                        EventDateRow(title: "创建时间", date: event.createdAt)
                        EventDateRow(title: "更新时间", date: event.updatedAt)
                    }
                }
            } else {
                ContentUnavailableView("事件已不存在", systemImage: "doc.text.magnifyingglass", description: Text("请返回当前时间线。"))
            }
        }
        .navigationTitle("事件详情")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if event != nil {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { editingEvent = event } label: {
                        Image(systemName: "pencil")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("编辑事件")
                    Button(role: .destructive) { showingDeleteConfirmation = true } label: {
                        Image(systemName: "trash")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("删除事件")
                }
            }
        }
        .sheet(item: $editingEvent) { event in
            EventEditorView(timelineID: timelineID, event: event)
                .environmentObject(store)
        }
        .alert("删除事件？", isPresented: $showingDeleteConfirmation) {
            Button("取消", role: .cancel) {}
            Button("删除", role: .destructive, action: delete)
        } message: {
            Text("将删除事件“\(event?.title ?? "")”。此操作无法撤销。")
        }
        .alert("操作未完成", isPresented: errorPresented) {
            Button("知道了", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func delete() {
        do {
            try store.deleteEvent(timelineID: timelineID, eventID: eventID)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }
}

private struct EventDateRow: View {
    let title: String
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .foregroundStyle(.secondary)
            Text(date.formatted(.dateTime.year().month().day().hour().minute().second().timeZone()))
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .padding(.vertical, 4)
    }
}