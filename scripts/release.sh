#!/usr/bin/env bash
# Release skyprint: 1 version cho Maven + plugin Flutter (REQ-012). Dùng: scripts/release.sh X.Y.Z [--publish]
# Mặc định KHÔNG publish/push/tag (chỉ build + xuất repo plugin); --publish thì publish GitHub Packages.
set -euo pipefail
VER="${1:?usage: scripts/release.sh X.Y.Z [--publish]}"
[[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version phải dạng X.Y.Z"; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"

[[ -z "$(git status --porcelain -- kmp flutter | grep -v '^??' || true)" ]] || { echo "working tree (kmp/flutter) chưa sạch — commit trước"; exit 1; }

sed -i '' -E "s/^(    version = )\"[^\"]+\"/\1\"$VER\"/" kmp/build.gradle.kts
sed -i '' -E "s/^version: .*/version: $VER/" flutter/skyprint_flutter/pubspec.yaml
sed -i '' -E "s#(skyprint-core-android:)[0-9A-Za-z.\-]+'#\1$VER'#" flutter/skyprint_flutter/android/build.gradle
sed -i '' -E "s#(ref: v)[0-9.]+#\1$VER#" flutter/skyprint_flutter/README.md

# kiểm 3 nơi khớp version
K=$(grep -E '^\s+version = ' kmp/build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
P=$(grep -E '^version:' flutter/skyprint_flutter/pubspec.yaml | awk '{print $2}')
G=$(grep -E "skyprint-core-android:" flutter/skyprint_flutter/android/build.gradle | sed -E "s/.*android:([^']+)'.*/\1/")
[[ "$K" == "$VER" && "$P" == "$VER" && "$G" == "$VER" ]] || { echo "version lệch: kmp=$K pubspec=$P gradle=$G"; exit 1; }

(cd kmp && ./gradlew clean build)
rm -rf flutter/skyprint_flutter/android/repo/com/dcorp/skyprint
(cd kmp && ./gradlew :skyprint-core:publishAndroidPublicationToFlutterPluginRepoRepository)
# iOS (REQ-012 gd2): XCFramework release -> ios/SkyprintCore.xcframework.zip (Package.swift trỏ path này)
(cd kmp && ./gradlew :skyprint-core:assembleSkyprintCoreReleaseXCFramework)
mkdir -p ios && rm -f ios/SkyprintCore.xcframework.zip
(cd kmp/skyprint-core/build/XCFrameworks/release && ditto -c -k --sequesterRsrc --keepParent SkyprintCore.xcframework "$ROOT/ios/SkyprintCore.xcframework.zip")
if [[ "${2:-}" == "--publish" ]]; then
  (cd kmp && ./gradlew :skyprint-core:publishAllPublicationsToGitHubPackagesRepository :skyprint-template:publishAllPublicationsToGitHubPackagesRepository)
fi
cat <<MSG
Xong v$VER. Việc còn lại (thủ công):
  1. cập nhật CHANGELOG.md
  2. git add -A && git commit -m "release: v$VER" && git tag v$VER && git push origin main --tags
MSG
