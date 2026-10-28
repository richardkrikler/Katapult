#!/bin/sh
# Same rules locally and in CI. Local install: brew install semgrep
# The logging rule flags every Log call regardless of content, so it is off.
exec semgrep scan --metrics=off --error \
  --config p/kotlin --config p/mobsfscan --config p/secrets --config p/github-actions \
  --exclude-rule mobsf.mobsfscan.kotlin.android.android_kotlin_logging \
  "$@"
