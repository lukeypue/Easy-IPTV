// swift-tools-version: 5.9
import PackageDescription
let package = Package(
    name: "RyzodCore", platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "RyzodCore", targets: ["RyzodCore"])],
    targets: [.target(name: "RyzodCore"), .testTarget(name: "RyzodCoreTests", dependencies: ["RyzodCore"])]
)
