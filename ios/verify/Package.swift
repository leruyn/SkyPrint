// swift-tools-version:5.9
// Test hồi quy REQ-012 gd2: package Swift tiêu thụ SkyprintCore qua Package.swift ở gốc repo.
// Chạy: cd ios/verify && xcodebuild test -scheme SwiftCheck -destination 'platform=iOS Simulator,name=iPhone 17 Pro'
import PackageDescription
let package = Package(
    name: "SwiftCheck",
    platforms: [.iOS(.v15)],
    products: [.library(name: "Check", targets: ["Check"])],
    dependencies: [.package(name: "Skyprint", path: "../..")],
    targets: [
        .target(name: "Check", dependencies: [.product(name: "Skyprint", package: "Skyprint")]),
        .testTarget(name: "CheckTests", dependencies: ["Check"]),
    ]
)
