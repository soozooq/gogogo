#!/usr/bin/env bash
# Runs against the CURRENT repository's real pure-Java V0 classes.
# No Android SDK, Gradle, internet, or extra dependencies needed.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v javac >/dev/null || ! command -v java >/dev/null; then
  echo "V0 JVM SMOKE FAIL: JDK (javac and java) required" >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"
cat >"$tmp/V0Smoke.java" <<'JAVA'
import com.soozooq.tianwaitian.fixture.GuestEntry;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0GuestDigestVerdict;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0IpcVerdict;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0TwoSlotVerdict;

public final class V0Smoke {
    private static int checks;

    private static void check(boolean good) {
        ++checks;
        if (!good) throw new AssertionError("failed check " + checks);
    }

    public static void main(String[] args) {
        check("alpha_42".equals(GuestEntry.record("alpha_42")));
        check("alpha_42".equals(GuestEntry.read()));
        try {
            GuestEntry.record("../escape");
            throw new AssertionError("invalid marker accepted");
        } catch (IllegalArgumentException expected) {
            check(true);
        }
        try {
            GuestEntry.record(null);
            throw new AssertionError("null marker accepted");
        } catch (IllegalArgumentException expected) {
            check(true);
        }

        String shaA = "0123456789abcdef".repeat(4);
        String shaB = "fedcba9876543210".repeat(4);
        check(VirtualContainerV0GuestDigestVerdict.validDigest(shaA));
        check(VirtualContainerV0GuestDigestVerdict.passed(shaA, shaA));
        check(!VirtualContainerV0GuestDigestVerdict.passed(shaA, shaB));
        check(!VirtualContainerV0GuestDigestVerdict.passed(null, shaA));
        check(!VirtualContainerV0GuestDigestVerdict.passed(
                shaA.toUpperCase(), shaA.toUpperCase()));

        check(VirtualContainerV0IpcVerdict.passed(100, 10123, 200, 10123));
        check(!VirtualContainerV0IpcVerdict.passed(100, 10123, 100, 10123));
        check(!VirtualContainerV0IpcVerdict.passed(100, 10123, 200, 10124));
        check(!VirtualContainerV0IpcVerdict.passed(0, 10123, 200, 10123));

        check(VirtualContainerV0TwoSlotVerdict.passed(
                100, 10123, 200, 10123, 300, 10123, "A", "A", "B", "B"));
        check(!VirtualContainerV0TwoSlotVerdict.passed(
                100, 10123, 200, 10123, 200, 10123, "A", "A", "B", "B"));
        check(!VirtualContainerV0TwoSlotVerdict.passed(
                100, 10123, 200, 10123, 300, 10124, "A", "A", "B", "B"));
        check(!VirtualContainerV0TwoSlotVerdict.passed(
                100, 10123, 200, 10123, 300, 10123, "A", "B", "B", "B"));

        System.out.println("V0 JVM smoke: " + checks + "/17 PASS");
        System.out.println("Scope: Java 11 source/bytecode compilation and predicates only;");
        System.out.println("NOT an Android APK build, DEX, Binder, or phone validation.");
    }
}
JAVA

javac --release 11 -d "$tmp/classes" \
  containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestEntry.java \
  app/src/main/java/com/zcshou/gogogo/virtualv0/VirtualContainerV0GuestDigestVerdict.java \
  app/src/main/java/com/zcshou/gogogo/virtualv0/VirtualContainerV0IpcVerdict.java \
  app/src/main/java/com/zcshou/gogogo/virtualv0/VirtualContainerV0TwoSlotVerdict.java \
  "$tmp/V0Smoke.java"
java -cp "$tmp/classes" V0Smoke
