import SwiftUI

struct TimelineDetailView: View {
    @EnvironmentObject private var store: TimelineStore
    let timelineID: String

    @State private var searchText = ""
    @State private var isAddingEvent = false
    @State private var editingTimeline: Timeline?
    @State private var editingEvent: TimelineEvent?
    @State private var eventToDelete: TimelineEvent?
    @State private var errorMessage: String?

    private var timeline: Timeline? {
        store.timeline(withID: timelineID)
    }

    var body: some View {
        Group {
            if let timeline {
                detailContent(timeline)
            } else {
                ContentUnavailableView("时间线已不存在", systemImage: "list.bullet.rectangle", description: Text("请返回时间线列表。"))
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(timeline?.title ?? "时间线")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "搜索事件标题或备注")
        .toolbar {
            if let timeline {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Picker("事件排序", selection: eventOrderBinding(for: timeline.id)) {
                            ForEach(EventOrder.allCases) { order in
                                Text(order.title).tag(order)
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("事件排序")

                    Button { editingTimeline = timeline } label: {
                        Image(systemName: "pencil")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("编辑时间线")

                    Button { isAddingEvent = true } label: {
                        Image(systemName: "plus")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel("新建事件")
                }
            }
        }
        .sheet(isPresented: $isAddingEvent) {
            EventEditorView(timelineID: timelineID)
                .environmentObject(store)
        }
        .sheet(item: $editingTimeline) { timeline in
            TimelineEditorView(timeline: timeline)
                .environmentObject(store)
        }
        .sheet(item: $editingEvent) { event in
            EventEditorView(timelineID: timelineID, event: event)
                .environmentObject(store)
        }
        .alert(
            "删除事件？",
            isPresented: Binding(
                get: { eventToDelete != nil },
                set: { if !$0 { eventToDelete = nil } }
            )
        ) {
            Button("取消", role: .cancel) { eventToDelete = nil }
            Button("删除", role: .destructive) {
                guard let event = eventToDelete else { return }
                eventToDelete = nil
                delete(event)
            }
        } message: {
            if let event = eventToDelete {
                Text("将删除事件“\(event.title)”。此操作无法撤销。")
            }
        }
        .alert("操作未完成", isPresented: errorPresented) {
            Button("知道了", role: .cancel) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
        .overlay(alignment: .bottom) {
            if let notice = store.notice {
                OperationNoticeBanner(text: notice) { store.clearNotice() }
            }
        }
        .task(id: store.notice) {
            guard store.notice != nil else { return }
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled else { return }
            store.clearNotice()
        }
    }

    @ViewBuilder
    private func detailContent(_ timeline: Timeline) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Circle()
                    .fill(timeline.color)
                    .frame(width: 12, height: 12)
                Text(searchKeyword.isEmpty
                     ? "\(timeline.events.count) 个事件 · \(store.eventOrder(for: timeline.id).title)"
                     : "找到 \(filteredEvents(timeline).count) / \(timeline.events.count) 个事件")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color(.secondarySystemGroupedBackground))

            if timeline.events.isEmpty {
                ContentUnavailableView {
                    Label("这条时间线还没有事件", systemImage: "sparkles")
                } description: {
                    Text("记录一个过去、现在或未来的重要节点。")
                } actions: {
                    Button("新建事件") { isAddingEvent = true }
                        .buttonStyle(.borderedProminent)
                }
            } else if filteredEvents(timeline).isEmpty {
                ContentUnavailableView {
                    Label("没有找到相关事件", systemImage: "magnifyingglass")
                } description: {
                    Text("搜索会同时匹配当前时间线的标题和备注。")
                } actions: {
                    Button("清除搜索") { searchText = "" }
                }
            } else {
                List {
                    ForEach(dayGroups(timeline)) { group in
                        Section {
                            ForEach(group.events) { event in
                                NavigationLink(value: EventRoute(timelineID: timeline.id, eventID: event.id)) {
                                    EventRow(event: event, tone: timeline.color)
                                }
                                .buttonStyle(.plain)
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) { eventToDelete = event } label: {
                                        Label("删除", systemImage: "trash")
                                    }
                                    Button { editingEvent = event } label: {
                                        Label("编辑", systemImage: "pencil")
                                    }
                                    .tint(TimelineTone.ocean.color)
                                }
                                .contextMenu {
                                    Button { editingEvent = event } label: {
                                        Label("编辑事件", systemImage: "pencil")
                                    }
                                    Button(role: .destructive) { eventToDelete = event } label: {
                                        Label("删除事件", systemImage: "trash")
                                    }
                                }
                            }
                        } header: {
                            Text(group.date.formatted(date: .complete, time: .omitted))
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .scrollContentBackground(.hidden)
            }
        }
    }

    private var searchKeyword: String {
        searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private func filteredEvents(_ timeline: Timeline) -> [TimelineEvent] {
        let sorted = sortEvents(timeline.events, order: store.eventOrder(for: timeline.id))
        guard !searchKeyword.isEmpty else { return sorted }
        return sorted.filter {
            $0.title.lowercased().contains(searchKeyword) || $0.body.lowercased().contains(searchKeyword)
        }
    }

    private func dayGroups(_ timeline: Timeline) -> [EventDayGroup] {
        let calendar = Calendar.current
        var groups: [EventDayGroup] = []
        for event in filteredEvents(timeline) {
            let day = calendar.startOfDay(for: event.occurredAt)
            if groups.last?.date == day {
                groups[groups.count - 1].events.append(event)
            } else {
                groups.append(EventDayGroup(date: day, events: [event]))
            }
        }
        return groups
    }

    private func eventOrderBinding(for id: String) -> Binding<EventOrder> {
        Binding(
            get: { store.eventOrder(for: id) },
            set: { store.setEventOrder($0, for: id) }
        )
    }

    private func delete(_ event: TimelineEvent) {
        do {
            try store.deleteEvent(timelineID: timelineID, eventID: event.id)
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

private struct EventDayGroup: Identifiable {
    var id: Date { date }
    let date: Date
    var events: [TimelineEvent]
}

private struct EventRow: View {
    let event: TimelineEvent
    let tone: Color

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(tone)
                .frame(width: 9, height: 9)
                .padding(.top, 7)
            VStack(alignment: .leading, spacing: 6) {
                Text(event.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                if !event.body.isEmpty {
                    Text(event.body)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                Text(event.occurredAt.formatted(date: .abbreviated, time: .short))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}