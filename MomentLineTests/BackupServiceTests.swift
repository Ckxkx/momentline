import XCTest
@testable import MomentLine

final class BackupServiceTests: XCTestCase {
    private let service = BackupService()

    func testRoundTripPreservesMomentAndDetectsDuplicate() throws {
        let moment = makeMoment()
        let data = try service.encodeBackup(moments: [moment], exportedAt: moment.createdAt)
        let decoded = try service.decodeBackup(data)
        let preview = try service.preview(data: data, localMoments: [moment])

        XCTAssertEqual(decoded.moments, [moment])
        XCTAssertEqual(preview.duplicates, [moment])
        XCTAssertTrue(preview.additions.isEmpty)
        XCTAssertTrue(preview.conflicts.isEmpty)
    }

    func testPreviewDistinguishesAdditionAndConflict() throws {
        let local = makeMoment()
        var conflict = local
        conflict.title = "备份中的标题"
        let addition = makeMoment(id: UUID(), title: "新时刻")
        let preview = try service.preview(
            data: try service.encodeBackup(moments: [conflict, addition]),
            localMoments: [local]
        )

        XCTAssertEqual(preview.additions, [addition])
        XCTAssertEqual(preview.conflicts, [conflict])
        XCTAssertTrue(preview.duplicates.isEmpty)
    }

    func testRejectsUnsupportedVersion() throws {
        let data = Data(#"{"format":"MomentLine","version":2,"exportedAt":"2026-01-01T00:00:00Z","moments":[]}"#.utf8)
        XCTAssertThrowsError(try service.decodeBackup(data)) { error in
            guard case BackupError.unsupportedVersion(2) = error else {
                return XCTFail("错误类型不符合预期")
            }
        }
    }

    func testRejectsTimestampWithoutTimeZone() throws {
        let data = Data(#"{"format":"MomentLine","version":1,"exportedAt":"2026-01-01T00:00:00Z","moments":[{"id":"00000000-0000-0000-0000-000000000001","title":"测试","body":"","occurredAt":"2026-01-01T00:00:00","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}]}"#.utf8)
        XCTAssertThrowsError(try service.decodeBackup(data)) { error in
            guard case let BackupError.invalidRecord(index, _) = error else {
                return XCTFail("错误类型不符合预期")
            }
            XCTAssertEqual(index, 0)
        }
    }

    func testRejectsDuplicateIdentifiersInsideBackup() throws {
        let moment = makeMoment()
        var root = try JSONSerialization.jsonObject(with: service.encodeBackup(moments: [moment])) as! [String: Any]
        let records = root["moments"] as! [[String: Any]]
        root["moments"] = [records[0], records[0]]
        let data = try JSONSerialization.data(withJSONObject: root)

        XCTAssertThrowsError(try service.decodeBackup(data)) { error in
            guard case let BackupError.invalidRecord(index, _) = error else {
                return XCTFail("错误类型不符合预期")
            }
            XCTAssertEqual(index, 1)
        }
    }

    func testAllowsEmptyBackup() throws {
        let data = try service.encodeBackup(moments: [])
        XCTAssertEqual(try service.decodeBackup(data).moments.count, 0)
    }

    private func makeMoment(id: UUID = UUID(), title: String = "一个时刻") -> Moment {
        let date = Date(timeIntervalSince1970: 1_767_225_600)
        return Moment(id: id, title: title, body: "第一行\n第二行", occurredAt: date, createdAt: date, updatedAt: date)
    }
}