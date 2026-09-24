// swift-tools-version:5.9
// SkyprintCore cho iOS Swift (REQ-012 giai đoạn 2). XCFramework nằm trong repo (ios/), sinh bởi scripts/release.sh.
import PackageDescription

let package = Package(
    name: "SkyprintCore",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "SkyprintCore", targets: ["SkyprintCore"]),
    ],
    targets: [
        .binaryTarget(name: "SkyprintCore", path: "ios/SkyprintCore.xcframework.zip"),
    ]
)
