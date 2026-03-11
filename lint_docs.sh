#!/bin/bash
# This script uses markdownlint.
# https://github.com/markdownlint/markdownlint
# To install, run:
# gem install mdl

set -ex

STYLE=.markdownlint.rb

# Intentionally opt-in only markdown files that are included in generated docs.
# Keep this list aligned with modules included by root dokka generation.
DOC_MARKDOWN_FILES=(
  "workflow-core/README.md"
  "workflow-runtime/README.md"
  "workflow-rx2/README.md"
  "workflow-testing/README.md"
  "workflow-ui/compose/README.md"
  "workflow-ui/radiography/README.md"
)

DOC_FILES_TO_LINT=()
for file in "${DOC_MARKDOWN_FILES[@]}"; do
  if [ -f "$file" ]; then
    DOC_FILES_TO_LINT+=("$file")
  fi
done

if [ ${#DOC_FILES_TO_LINT[@]} -eq 0 ]; then
  echo "No opted-in markdown files found to lint."
  exit 0
fi

mdl --style "$STYLE" --ignore-front-matter "${DOC_FILES_TO_LINT[@]}"

echo "Success."
