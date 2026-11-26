import SwiftUI

struct EventRoute: Hashable {
    let timelineID: String
    let eventID: String
}

struct TimelineView: View {
    @EnvironmentObject private var store: TimelineStore
    @State private var searchText = ""
    @State private var isAdding = false
    @State private var editingTimeline: Timeline?
    @State private var timelineToDelete: Timeline?
    @State private var showingSettings = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            content
                .background(Color(.systemGroupedBackground))
                .navigationTitle("时间线")
                .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .automatic), prompt: "搜索时间线名称")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { showingSettings = true } label: {
                            Image(systemName: "gearshape")
                                .frame(minWidth: 44, minHeight: 44)
                        }
                        .accessibilityLabel("设置与数据")
                    }
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        Menu {
                            Picker("时间线排序", selection: $store.timelineOrder) {
                                ForEach(TimelineOrder.allCases) { order in
                                    Text(order.title).tag(order)
                                }
                            }
                        } label: {
                            Image(systemName: "arrow.up.arrow.down")
                                .frame(minWidth: 44, minHeight: 44)
                        }
                        .accessibilityLabel("时间线排序")

                        Button { isAdding = true } label: {
                            Image(systemName: "plus")
                                .frame(minWidth: 44, minHeight: 44)
                        }
                        .accessibilityLabel("新建时间线")
                        .disabled(store.loadError != nil)
                    }
                }
                .navigationDestination(for: String.self) { timelineID in
                    TimelineDetailView(timelineID: timelineID)
                }
                .navigationDestination(for: EventRoute.self) { route in
                    EventDetailView(timelineID: route.timelineID, eventID: route.eventID)
                }
                .sheet(isPresented: $isAdding) {
                    TimelineEditorView()
                        .environmentObject(store)
                }
                .sheet(item: $editingTimeline) { timeline in
                    TimelineEditorView(timeline: timeline)
                        .environmentObject(store)
                }
                .sheet(isPresented: $showingSettings) {
                    SettingsView()
                        .environmentObject(store)
                }
                .alert(
                    "删除时间线？",
                    isPresented: Binding(
                        get: { timelineToDelete != nil },
                        set: { if !$0 { timelineToDelete = nil } }
                    )
                ) {
                    Button("取消", role: .cancel) { timelineToDelete = nil }
                    Button("删除", role: .destructive) {
                        guard let timeline = timelineToDelete else { return }
                        timelineToDelete = nil
                        do {
                            try store.deleteTimeline(id: timeline.id)
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                    }
                } message: {
                    if let timeline = timelineToDelete {
                        Text("将删除“\(timeline.title)”及其 \(timeline.events.count) 个事件。此操作无法撤销。")
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
    }

    @ViewBuilder
    private var content: some View {
        if let error = store.loadError {
            ContentUnavailableView {
                Label("无法读取本地数据", systemImage: "exclamationmark.circle")
            } description: {
                Text(error)
            } actions: {
                Button("重试") { store.load() }
                    .buttonStyle(.borderedProminent)
            }
        } else if store.timelines.isEmpty {
            ContentUnavailableView {
                Label("创建你的第一条时间线", systemImage: "list.bullet.rectangle")
            } description: {
                Text("按主题收藏每一个重要节点，数据仅保存在当前设备。")
            } actions: {
                Button("新建时间线") { isAdding = true }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            }
        } else if filteredTimelines.isEmpty {
            ContentUnavailableView {
                Label("没有找到相关时间线", systemImage: "magnifyingglass")
            } description: {
                Text("试试其他名称关键词。")
            } actions: {
                Button("清除搜索") { searchText = "" }
            }
        } else {
            List {
                Section {
                    ForEach(filteredTimelines) { timeline in
                        NavigationLink(value: timeline.id) {
                            TimelineCard(timeline: timeline)
                        }
                        .buttonStyle(.plain)
                        .listRowInsets(EdgeInsets(top: 7, leading: 20, bottom: 7, trailing: 20))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) { timelineToDelete = timeline } label: {
                                Label("删除", systemImage: "trash")
                            }
                            Button { editingTimeline = timeline } label: {
                                Label("编辑", systemImage: "pencil")
                            }
                            .tint(TimelineTone.ocean.color)
                        }
                        .contextMenu {
                            Button { editingTimeline = timeline } label: {
                                Label("编辑时间线", systemImage: "pencil")
                            }
                            Button(role: .destructive) { timelineToDelete = timeline } label: {
                                Label("删除时间线", systemImage: "trash")
                            }
                        }
                    }
                } header: {
                    Text(searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                         ? "共 \(store.timelines.count) 条时间线 · \(store.totalEventCount) 个事件"
                         : "找到 \(filteredTimelines.count) 条时间线")
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private var filteredTimelines: [Timeline] {
        let keyword = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !keyword.isEmpty else { return store.orderedTimelines }
        return store.orderedTimelines.filter { $0.title.lowercased().contains(keyword) }
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }
}

private struct TimelineCard: View {
    let timeline: Timeline

    var body: some View {
        HStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(timeline.color)
                .frame(width: 7, height: 62)
            VStack(alignment: .leading, spacing: 8) {
                Text(timeline.title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .lineLimit(3)
                Text("\(timeline.events.count) 个事件")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 22)
        .frame(maxWidth: .infinity, minHeight: 112, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint("打开时间线")
    }
}

struct OperationNoticeBanner: View {
    let text: String
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
            Text(text)
                .multilineTextAlignment(.leading)
            Spacer(minLength: 0)
            Button("关闭", action: dismiss)
                .font(.footnote)
        }
        .font(.subheadline.weight(.medium))
        .foregroundStyle(.white)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 48)
        .background(Color(.darkGray).opacity(0.96), in: Capsule())
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
        .accessibilityElement(children: .combine)
    }
}