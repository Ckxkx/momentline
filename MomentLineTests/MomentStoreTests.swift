import XCTest
@testable import MomentLine

@MainActor
final class MomentStoreTests: XCTestCase {
    private var directory: URL!
    private var fileURL: URL!
    private var defaults: UserDefaults!
    private var suiteName: String!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        fileURL = directory.appendingPathComponent("moments.json")
        suiteName = "MomentLineTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        defaults.removePersistentDomain(forName: suiteName)
        defaults = nil
        fileURL = nil
        directory = nil
        suiteName = nil
        super.tearDown()
    }

    func testSavedMomentAndPreferencesSurviveReload() throws {
        let store = MomentStore(fileURL: fileURL, userDefaults: defaults)
        let date = Date(timeIntervalSince1970: 1_767_225_600)
        let saved = try store.add(title: "  持久化标题  ", body: "第一行\n第二行", occurredAt: date)
        store.order = .oldestFirst
        store.appearance = .dark

        let reloaded = MomentStore(fileURL: fileURL, userDefaults: defaults)
        XCTAssertEqual(reloaded.moments, [saved])
        XCTAssertEqual(reloaded.moments.first?.title, "持久化标题")
        XCTAssertEqual(reloaded.moments.first?.body, "第一行\n第二行")
        XCTAssertEqual(reloaded.order, .oldestFirst)
        XCTAssertEqual(reloaded.appearance, .dark)
    }

    func testEmptyTitleDoesNotChangeStoredData() {
        let store = MomentStore(fileURL: fileURL, userDefaults: defaults)
        XCTAssertThrowsError(try store.add(title: " \n ", body: "保留", occurredAt: Date()))
        XCTAssertTrue(store.moments.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: fileURL.path))
    }

    func testConflictCanKeepLocalOrUseBackup() throws {
        let store = MomentStore(fileURL: fileURL, userDefaults: defaults)
        let local = try store.add(title: "本地标题", body: "", occurredAt: Date())
        var backup = local
        backup.title = "备份标题"
        let data = try BackupService().encodeBackup(moments: [backup])
        let preview = try store.prepareImport(data: data)

        let keepResult = try store.applyImport(preview, resolution: .keepLocal)
        XCTAssertEqual(keepResult.skipped, 1)
        XCTAssertEqual(store.moments.first?.title, "本地标题")

        let replaceResult = try store.applyImport(preview, resolution: .useBackup)
        XCTAssertEqual(replaceResult.replaced, 1)
        XCTAssertEqual(store.moments.first?.title, "备份标题")
    }

    func testReadFailurePreventsOverwritingOriginalFile() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let originalData = Data("损坏的本地数据".utf8)
        try originalData.write(to: fileURL)
        let store = MomentStore(fileURL: fileURL, userDefaults: defaults)

        XCTAssertNotNil(store.loadError)
        XCTAssertThrowsError(try store.add(title: "不能覆盖", occurredAt: Date()))
        XCTAssertEqual(try Data(contentsOf: fileURL), originalData)
    }

    func testOldestAndNewestOrdering() throws {
        let store = MomentStore(fileURL: fileURL, userDefaults: defaults)
        let old = try store.add(title: "较早", occurredAt: Date(timeIntervalSince1970: 100))
        let new = try store.add(title: "较新", occurredAt: Date(timeIntervalSince1970: 200))

        XCTAssertEqual(store.sortedMoments.map(\.id), [new.id, old.id])
        store.order = .oldestFirst
        XCTAssertEqual(store.sortedMoments.map(\.id), [old.id, new.id])
    }
}