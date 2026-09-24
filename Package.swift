// swift-tools-version:5.9
// Skyprint cho iOS Swift (REQ-012 gd2, REQ-013): framework tổng = skyprint-core + skyprint-template.
// XCFramework nằm trong repo (ios/), sinh bởi scripts/release.sh.
import PackageDescription

let package = Package(
    name: "Skyprint",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "Skyprint", targets: ["Skyprint"]),
    ],
    targets: [
        .binaryTarget(name: "Skyprint", path: "ios/Skyprint.xcframework.zip"),
    ]
)
