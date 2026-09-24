import XCTest
@testable import Check

final class CheckTests: XCTestCase {
    func testEncodeProducesBytes() {
        XCTAssertGreaterThan(encodeSample(), 10)
    }
}
