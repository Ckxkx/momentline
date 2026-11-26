import SwiftUI

struct TimelineEditorView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss

    private let timeline: Timeline?
    @State private var title: String
    @State private var tone: String
    @State private var showingDiscardConfirmation = false
    @State private var errorMessage: String?

    init(timeline: Timeline? = nil) {
        self.timeline = timeline
        _title = State(initialValue: timeline?.title ?? "")
        _tone = State(initialValue: timeline?.tone ?? TimelineTone.sage.rawValue)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("例如：宝宝成长、产品迭代", text: $title, axis: .vertical)
                        .lineLimit(1...3)
                        .submitLabel(.done)
                } header: {
                    Text("名称")
                } footer: {
                    Text("\(title.count)/80，保存时会去除首尾空白")
                }

                Section("主题颜色") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 16) {
                        ForEach(TimelineTone.allCases) { item in
                            Button { tone = item.rawValue } label: {
                                ZStack {
                                    Circle()
                                        .fill(item.color)
                                        .frame(width: 38, height: 38)
                                    if tone == item.rawValue {
                                        Image(systemName: "checkmark")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(.white)
                                    }
                                }
                                .frame(minWidth: 52, minHeight: 52)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(item.title)，\(item.rawValue)")
                            .accessibilityAddTraits(tone == item.rawValue ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            .navigationTitle(timeline == nil ? "新建时间线" : "编辑时间线")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: cancel)
                        .frame(minWidth: 44, minHeight: 44)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(timeline == nil ? "创建" : "保存", action: save)
                        .fontWeight(.semibold)
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .interactiveDismissDisabled(isDirty)
            .confirmationDialog("放弃未保存的内容？", isPresented: $showingDiscardConfirmation, titleVisibility: .visible) {
                Button("放弃", role: .destructive) { dismiss() }
                Button("继续编辑", role: .cancel) {}
            }
            .alert("无法保存时间线", isPresented: errorPresented) {
                Button("知道了", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private var isDirty: Bool {
        title != (timeline?.title ?? "") || tone != (timeline?.tone ?? TimelineTone.sage.rawValue)
    }

    private func cancel() {
        if isDirty {
            showingDiscardConfirmation = true
        } else {
            dismiss()
        }
    }

    private func save() {
        do {
            if let timeline {
                try store.updateTimeline(id: timeline.id, title: title, tone: tone)
            } else {
                _ = try store.addTimeline(title: title, tone: tone)
            }
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