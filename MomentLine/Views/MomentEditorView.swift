import SwiftUI

struct TimelineEditorView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var tone = TimelineTone.sage
    @State private var showingDiscardConfirmation = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("名称") {
                    TextField("例如：宝宝成长、产品迭代", text: $title)
                        .submitLabel(.done)
                }

                Section("颜色") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 18) {
                        ForEach(TimelineTone.allCases) { item in
                            Button { tone = item } label: {
                                ZStack {
                                    Circle()
                                        .fill(item.color)
                                        .frame(width: 38, height: 38)
                                    if tone == item {
                                        Image(systemName: "checkmark")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(.white)
                                    }
                                }
                                .frame(minWidth: 44, minHeight: 44)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(item.title)
                            .accessibilityAddTraits(tone == item ? .isSelected : [])
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            .navigationTitle("新建时间线")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        if title.isEmpty {
                            dismiss()
                        } else {
                            showingDiscardConfirmation = true
                        }
                    }
                    .frame(minWidth: 44, minHeight: 44)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("创建", action: save)
                        .fontWeight(.semibold)
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .interactiveDismissDisabled(!title.isEmpty)
            .confirmationDialog("放弃未保存的内容？", isPresented: $showingDiscardConfirmation, titleVisibility: .visible) {
                Button("放弃", role: .destructive) { dismiss() }
                Button("继续编辑", role: .cancel) {}
            }
            .alert("无法创建", isPresented: errorPresented) {
                Button("知道了", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func save() {
        do {
            try store.add(title: title, tone: tone)
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