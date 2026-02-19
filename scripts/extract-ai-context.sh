#!/usr/bin/env bash
#
# Interactive wrapper for the extractAiContext Gradle task.
# Run this from any project that applies the com.squareup.workflow1.ai-context plugin.
#
set -euo pipefail

./gradlew extractAiContext --preview 2>&1 | grep -v "^$\|Deprecated\|--warning-mode\|gradle.org\|Problems report\|Incubating"
echo

read -rp "Extract skills and update AGENTS.md? [Y/n] " answer
case "${answer:-Y}" in
  [nN]*) echo "Aborted."; exit 0 ;;
esac

echo
./gradlew extractAiContext --quiet
