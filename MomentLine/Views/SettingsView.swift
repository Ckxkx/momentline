import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: TimelineStore
    @Environment(\.dismiss) private var dismiss
    @State private var exportDocument: JSONDocument?
    @State private var exportFilename = ""
    @State private var exportTimelineCount = 0
    @State private var exportEventCount = 0
    @State private var showingExportWarning = false
    @State private var showingExporter = false
    @State private var showingImporter = false
    @State private var importPreview: ImportPreview?
    @State private var alertTitle = ""
    @State private var alertMessage = ""
    @State private var showingAlert = false

    var body: some View {
        NavigationStack {
            Form {
                Section("外观") {
                    Picker("显示模式", selection: $store.appearance) {
                        ForEach(AppAppearance.allCases) { appearance in
                            Text(appearance.title).tag(appearance)
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                }

                Section("数据管理") {
                    LabeledContent("本地时间线", value: "\(store.timelines.count) 条")
                    LabeledContent("本地事件", value: "\(store.totalEventCount) 个")
                    Button { prepareExport() } label: {
                        Label("导出完整备份", systemImage: "square.and.arrow.up")
                            .frame(minHeight: 44)
                    }
                    .disabled(store.loadError != nil)
                    Button { showingImporter = true } label: {
                        Label("从备份导入", systemImage: "square.and.arrow.down")
                            .frame(minHeight: 44)
                    }
                    .disabled(store.loadError != nil)
                } footer: {
                    Text("即使没有时间线也可以导出空备份。数据仅保存在当前设备，备份采用 UTF-8 JSON v2 格式。")
                }

                Section {
                    LabeledContent("存储方式", value: "本地离线 · 原子写入")
                    LabeledContent("备份格式", value: "MomentLine / v2")
                } header: {
                    Text("关于")
                } footer: {
                    Text("Moment Line 不需要账号、网络或额外设备权限。显示偏好不会随备份导入而改变。")
                }
            }
            .navigationTitle("设置与数据")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
            .alert("妥善保管备份", isPresented: $showingExportWarning) {
                Button("取消", role: .cancel) {}
                Button("继续") { showingExporter = true }
            } message: {
                Text("备份包含全部时间线、事件标题和备注等个人内容。请仅保存到你信任的位置，并妥善保管文件。")
            }
            .fileExporter(
                isPresented: $showingExporter,
                document: exportDocument,
                contentType: .json,
                defaultFilename: exportFilename,
                onCompletion: handleExport
            )
            .fileImporter(
                isPresented: $showingImporter,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false,
                onCompletion: handleFileSelection
            )
            .sheet(item: $importPreview) { preview in
                ImportPreviewView(preview: preview)
                    .environmentObject(store)
            }
            .alert(alertTitle, isPresented: $showingAlert) {
                Button("知道了", role: .cancel) {}
            } message: {
                Text(alertMessage)
            }
        }
    }

    private func prepareExport() {
        do {
            exportDocument = JSONDocument(data: try store.makeBackupData())
            exportTimelineCount = store.timelines.count
            exportEventCount = store.totalEventCount
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyyMMdd-HHmmss"
            exportFilename = "MomentLine-\(formatter.string(from: Date())).json"
            showingExportWarning = true
        } catch {
            present(title: "导出失败", message: error.localizedDescription)
        }
    }

    private func handleExport(_ result: Result<URL, Error>) {
        switch result {
        case let .success(url):
            present(
                title: "导出完成",
                message: "\(url.lastPathComponent)\n已导出 \(exportTimelineCount) 条时间线、\(exportEventCount) 个事件。"
            )
        case let .failure(error):
            if isCancellation(error) { return }
            store.recordFailure(operation: "export", stage: "fileExporter", error: error)
            present(title: "导出失败", message: "备份文件未能导出，请检查目的地权限或存储空间。")
        }
    }

    private func handleFileSelection(_ result: Result<[URL], Error>) {
        switch result {
        case let .success(urls):
            guard let url = urls.first else { return }
            let accessing = url.startAccessingSecurityScopedResource()
            defer {
                if accessing { url.stopAccessingSecurityScopedResource() }
            }
            do {
                let data = try Data(contentsOf: url)
                importPreview = try store.prepareImport(data: data)
            } catch {
                present(title: "无法导入", message: error.localizedDescription)
            }
        case let .failure(error):
            if isCancellation(error) { return }
            store.recordFailure(operation: "import", stage: "fileSelection", error: error)
            present(title: "无法读取文件", message: "请确认文件可访问后重试。")
        }
    }

    private func isCancellation(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == NSCocoaErrorDomain && nsError.code == NSUserCancelledError
    }

    private func present(title: String, message: String) {
        alertTitle = title
        alertMessage = message
        showingAlert = true
    }
}