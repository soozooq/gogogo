#!/usr/bin/env bash
set -euo pipefail
spec_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
work_root=$(cd -- "${CONTAINER_RESEARCH_WORKDIR:?Set a research directory outside the GoGoGo checkout}" && pwd)
candidate=${1:?Specify blackbox or virtualapp}
shift
: "${JAVA_HOME:?JDK 17 is required}"
: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:?Set ANDROID_SDK_ROOT}}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT
export CONTAINER_RESEARCH_SPEC_DIR="$spec_root"
export CONTAINER_RESEARCH_WORKDIR="$work_root"
case "$candidate" in
    blackbox) source_dir="$work_root/blackbox-api33" ; expected_commit=df6249da4b032649689a98242f147da6c5342172 ;;
    virtualapp) source_dir="$work_root/virtualapp-api33" ; expected_commit=b3c634ad7941765df3da84a207aca94b7861afae ;;
    *) exit 2 ;;
esac
test "$(git -C "$source_dir" rev-parse HEAD)" = "$expected_commit"
python3 "$spec_root/tools/prepare.py" --workdir "$work_root" --candidate "$candidate" --skip-guest --verify-only > /dev/null
cd -- "$source_dir"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
exec bash ./gradlew --init-script "$spec_root/tools/build-environment.init.gradle" \
    -Pandroid.builder.sdkDownload=false --no-daemon --max-workers=2 \
    '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' --console=plain "$@"
