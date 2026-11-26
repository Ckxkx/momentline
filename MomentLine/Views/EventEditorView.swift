import SwiftUI

struct EventEditorView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss

    private let timelineID: String
    private let event: TimelineEvent?
    private let initialOccurredAt: Date
    @State private var title: String
    @State private var bodyText: String
    @State private var occurredAt: Date
    @State private var showingDiscardConfirmation = false
    @State private var errorMessage: String?

    init(timelineID: String, event: TimelineEvent? = nil) {
        self.timelineID = timelineID
        self.event = event
        let initialDate = event?.occurredAt ?? Date()
        initialOccurredAt = initialDate
        _title = State(initialValue: event?.title ?? "")
        _bodyText = State(initialValue: event?.body ?? "")
        _occurredAt = State(initialValue: initialDate)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("事件标题", text: $title, axis: .vertical)
                        .lineLimit(1...4)
                } footer: {
                    Text("\(title.count)/100，标题不能为空")
                }

                Section {
                    ZStack(alignment: .topLeading) {
                        TextEditor(text: $bodyText)
                            .frame(minHeight: 140)
                        if bodyText.isEmpty {
                            Text("可选备注，支持换行")
                                .foregroundStyle(.tertiary)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 8)
                                .allowsHitTesting(false)
                        }
                    }
                } header: {
                    Text("备注")
                } footer: {
                    Text("\(bodyText.count)/5000")
                }

                Section("发生时间") {
                    DatePicker(
                        "发生时间",
                        selection: $occurredAt,
                        displayedComponents: [.date, .hourAndMinute]
                    )
                    .datePickerStyle(.compact)
                }
            }
            .navigationTitle(event == nil ? "新建事件" : "编辑事件")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消", action: cancel)
                        .frame(minWidth: 44, minHeight: 44)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(event == nil ? "创建" : "保存", action: save)
                        .fontWeight(.semibold)
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .interactiveDismissDisabled(isDirty)
            .confirmationDialog("放弃未保存的内容？", isPresented: $showingDiscardConfirmation, titleVisibility: .visible) {
                Button("放弃", role: .destructive) { dismiss() }
                Button("继续编辑", role: .cancel) {}
            }
            .alert("无法保存事件", isPresented: errorPresented) {
                Button("知道了", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private var isDirty: Bool {
        title != (event?.title ?? "")
            || bodyText != (event?.body ?? "")
            || occurredAt != initialOccurredAt
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
            if let event {
                try store.updateEvent(
                    timelineID: timelineID,
                    eventID: event.id,
                    title: title,
                    body: bodyText,
                    occurredAt: occurredAt
                )
            } else {
                _ = try store.addEvent(
                    timelineID: timelineID,
                    title: title,
                    body: bodyText,
                    occurredAt: occurredAt
                )
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