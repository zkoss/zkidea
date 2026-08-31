#!/usr/bin/env bash
#
# HTTP-level probe for the preview launcher's docroot static file serving.
#
# Starts zk-preview-launcher against this project's src/main/webapp, then requests the
# three static-asset fixture pages and the five assets they reference. Exits zero when
# all nine are served correctly. Regression guard for the docroot file route added for
# zkoss/zkidea#70 -- before that route, the five asset rows all returned 404.
#
# Usage:  ./tools/static-asset-probe.sh [path/to/zk-preview-launcher.jar]
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
WEBAPP="$HERE/src/main/webapp"

JAR="${1:-}"
if [[ -z "$JAR" ]]; then
    JAR="$(ls -t "$REPO"/zk-preview-launcher/build/release/zk-preview-launcher-*.jar \
                  "$REPO"/zk-preview-launcher/build/libs/zk-preview-launcher.jar 2>/dev/null | head -1)"
fi
if [[ ! -f "$JAR" ]]; then
    echo "launcher jar not found; build it with (cd $REPO/zk-preview-launcher && ./gradlew jar)" >&2
    exit 2
fi

CP_FILE="$HERE/target/preview-probe-classpath.txt"
if [[ ! -s "$CP_FILE" ]]; then
    echo "resolving classpath via maven..."
    mvn -q -f "$HERE/pom.xml" dependency:build-classpath -Dmdep.outputFile="$CP_FILE" || exit 2
fi
CP="$(cat "$CP_FILE"):$HERE/target/classes"

JAVA_BIN=java
command -v withjdk.sh >/dev/null && JAVA_BIN="withjdk.sh 17 java"

LOG="$(mktemp -t preview-probe)"
# shellcheck disable=SC2086
$JAVA_BIN -jar "$JAR" --classpath "$CP" --webapp "$WEBAPP" --port 0 >"$LOG" 2>&1 &
LAUNCHER_PID=$!
trap 'kill $LAUNCHER_PID 2>/dev/null; rm -f "$LOG"' EXIT

PORT=""
for _ in $(seq 1 200); do
    PORT="$(grep -m1 -oE 'PREVIEW_PORT=[0-9]+' "$LOG" | cut -d= -f2)"
    [[ -n "$PORT" ]] && break
    sleep 0.25
done
if [[ -z "$PORT" ]]; then
    echo "launcher never printed PREVIEW_PORT; log follows:" >&2
    cat "$LOG" >&2
    exit 2
fi
BASE="http://127.0.0.1:$PORT"
echo "launcher $(basename "$JAR") on $BASE, docroot $WEBAPP"
echo

fails=0
probe() { # path  expected_status  expected_content_type_substring  label
    local path="$1" want_status="$2" want_ct="$3" label="$4"
    local out status ct size verdict
    out="$(curl -s -o /dev/null -w '%{http_code} %{content_type} %{size_download}' "$BASE$path")"
    read -r status ct size <<<"$out"
    if [[ "$status" == "$want_status" && "$ct" == *"$want_ct"* && "$size" -gt 0 ]]; then
        verdict="PASS"
    else
        verdict="FAIL"
        fails=$((fails + 1))
    fi
    printf '%-4s %-46s got %-4s %-26s %7s bytes   want %s %s   (%s)\n' \
        "$verdict" "$path" "$status" "$ct" "$size" "$want_status" "$want_ct" "$label"
}

echo "-- fixture pages (must render; these already work) --"
probe /preview/static/image-assets.zul 200 text/html "case 1, images"
probe /preview/static/stylesheet.zul   200 text/html "case 2, stylesheet"
probe /preview/static/script.zul       200 text/html "case 3, script"
echo
echo "-- control: ZK classpath asset (already works) --"
probe /zkau/web/zk/img/zkpowered.png   200 image/png "classpath resource handler"
echo
echo "-- docroot static assets (the docroot file route) --"
probe /preview/static/assets/docroot-logo.png 200 image/png       "spec R1, R2"
probe /preview/static/assets/docroot-icon.svg 200 image/svg+xml   "spec R1, R2"
probe /preview/static/assets/docroot.css      200 text/css        "spec R1, R2"
probe /preview/static/assets/docroot.js       200 text/javascript "spec R1, R2"
probe /preview/static/assets/docroot.txt      200 text/plain      "spec R1, R2"
echo
if [[ $fails -eq 0 ]]; then
    echo "ALL PASS: the launcher serves docroot static files."
else
    echo "$fails FAILED. The launcher is not serving static files from the webapp docroot."
    echo "See tasks/launcher-static-asset-serving-spec.md and doc/preview-launcher-architecture.md."
fi
exit $((fails > 0))
