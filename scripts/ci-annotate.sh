#!/usr/bin/env bash
# Turns Gradle/Kotlin/AAPT failures from a build log into GitHub Actions annotations,
# so compile errors are visible on the run page (and through the check-run annotations API)
# without downloading raw logs. Usage: scripts/ci-annotate.sh <label> <logfile>
set -u
label="${1:-build}"
log="${2:-build.log}"
[ -f "$log" ] || 
# Full lint findings (the console only prints the first one)
report="native-android/app/build/reports/lint-results-debug.txt"
if [ -f "$report" ]; then
  grep -E ': (Error|Warning): ' "$report" | sed 's#/home/runner/work/[^/]*/[^/]*/native-android/app/##' | grep ': Error: ' | head -n 60 | split -l 10 - /tmp/lint_err_
  for f in /tmp/lint_err_*; do [ -f "$f" ] && emit "Lint errors" < "$f"; done
  rm -f /tmp/lint_err_*
fi
exit 0

emit() { # title, body-lines-on-stdin
  local body
  body="$(sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"} {print}')"
  [ -n "$body" ] && echo "::error title=${label}: $1::${body}"
}

# Kotlin compiler errors / warnings-as-errors
grep -E '^e: ' "$log" | sed 's#file:///[^ ]*/native-android/##' | head -n 40 | split -l 10 - /tmp/kt_err_
for f in /tmp/kt_err_*; do [ -f "$f" ] && emit "Kotlin compile errors" < "$f"; done
rm -f /tmp/kt_err_*

# Gradle "What went wrong" block
awk '/\* What went wrong:/{flag=1;next} /\* Try:/{flag=0} flag' "$log" | head -n 25 | emit "Gradle failure"

# Resource / manifest / lint problems
grep -E 'AAPT|error:|Manifest merger failed|Lint found|Execution failed for task' "$log" | head -n 15 | emit "Build problems"

# Test failures
grep -E 'FAILED|AssertionError|Exception:' "$log" | head -n 15 | emit "Test failures"

# Full lint findings (the console only prints the first one)
report="native-android/app/build/reports/lint-results-debug.txt"
if [ -f "$report" ]; then
  grep -E ': (Error|Warning): ' "$report" | sed 's#/home/runner/work/[^/]*/[^/]*/native-android/app/##' | grep ': Error: ' | head -n 60 | split -l 10 - /tmp/lint_err_
  for f in /tmp/lint_err_*; do [ -f "$f" ] && emit "Lint errors" < "$f"; done
  rm -f /tmp/lint_err_*
fi
exit 0
