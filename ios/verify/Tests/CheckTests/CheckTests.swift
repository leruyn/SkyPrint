import XCTest
@testable import Check

final class CheckTests: XCTestCase {
    func testEncodeProducesBytes() {
        XCTAssertGreaterThan(encodeSample(), 10)
    }

    func testTemplateRendersElements() {
        // text + 2 dòng món (group each) + cut = 4
        XCTAssertEqual(renderTemplateSample(), 4)
    }
}
