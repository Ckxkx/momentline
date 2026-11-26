import SwiftUI

struct ImportPreviewView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss
    @State private var resolution: ConflictResolution?
    @State private var errorMessage: String?
    @State private var result: ImportResult?

    let preview: ImportPreview

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("已检查完整备份。确认前不会更改本地数据；导入只合入新增及选定冲突，不删除本地独有内容。")
                        .foregroundStyle(.secondary)
                }

                Section("时间线") {
                    StatsRows(stats: preview.timelineStats, unit: "条")
                }

                Section("事件") {
                    StatsRows(stats: preview.eventStats, unit: "个")
                }

                if preview.hasConflicts {
                    Section {
                        Picker("冲突处理", selection: $resolution) {
                            Text("请选择").tag(ConflictResolution?.none)
                            ForEach(ConflictResolution.allCases) { resolution in
                                Text(resolution.title).tag(ConflictResolution?.some(resolution))
                            }
                        }
                        if let resolution {
                            Text(resolution.detail)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    } header: {
                        Text("需要你的选择")
                    } footer: {
                        Text("所选方式将应用于全部时间线与事件冲突，不会默认覆盖。")
                    }
                }

                Section {
                    Button("确认导入", action: applyImport)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .disabled(
                            preview.timelineStats.invalid > 0
                                || preview.eventStats.invalid > 0
                                || (preview.hasConflicts && resolution == nil)
                        )
                } footer: {
                    if preview.timelineStats.added == 0,
                       preview.timelineStats.duplicates == 0,
                       preview.timelineStats.conflicts == 0 {
                        Text("这是一个空备份，可以安全确认；本地数据不会发生变化。")
                    }
                }
            }
            .navigationTitle("导入预览")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .alert("导入未完成", isPresented: errorPresented) {
                Button("知道了", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
            .alert(
                "导入完成",
                isPresented: Binding(
                    get: { result != nil },
                    set: { if !$0 { result = nil } }
                )
            ) {
                Button("完成") { dismiss() }
            } message: {
                if let result {
                    Text(resultSummary(result))
                }
            }
        }
    }

    private func applyImport() {
        do {
            result = try store.applyImport(preview, resolution: resolution)
        } catch {
            errorMessage = "导入未完成，本地数据已保持不变。\(error.localizedDescription)"
        }
    }

    private func resultSummary(_ result: ImportResult) -> String {
        "时间线：新增 \(result.timelines.added)，跳过 \(result.timelines.skipped)，替换 \(result.timelines.replaced)。\n事件：新增 \(result.events.added)，跳过 \(result.events.skipped)，替换 \(result.events.replaced)。"
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }
}

private struct StatsRows: View {
    let stats: ImportClassStats
    let unit: String

    var body: some View {
        LabeledContent("新增", value: "\(stats.added) \(unit)")
        LabeledContent("重复并跳过", value: "\(stats.duplicates) \(unit)")
        LabeledContent("内容冲突", value: "\(stats.conflicts) \(unit)")
        LabeledContent("无效", value: "\(stats.invalid) \(unit)")
    }
}