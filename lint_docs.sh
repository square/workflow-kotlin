#!/bin/bash
# This script uses markdownlint.
# https://github.com/markdownlint/markdownlint
# To install, run:
# gem install mdl

set -ex

STYLE=.markdownlint.rb

TUTORIALS_DIR='./samples/tutorial'
TUTORIALS_STYLE=.markdownlint-tutorials.rb

# CHANGELOG is an mkdocs redirect pointer, not valid markdown.
# The benchmarks markdown file started failing on existing markdown that doesn't violate the failed
# check, might be an mdl bug.
find . \
    -name '*.md' \
    -not -name 'CHANGELOG.md' \
    -not -name 'AGENTS.md' \
    -not -name 'SKILL.md' \
    -not -name 'RULES.md' \
    -not -path './.github/*' \
    -not -path $TUTORIALS_DIR/'*' \
    -not -path './compose/*' \
    -not -path './benchmarks/*' \
    -not -path './build/*' \
    -not -path './thoughts/*' \
    | xargs mdl --style $STYLE --ignore-front-matter \

find $TUTORIALS_DIR \
    -name '*.md' \
    -not -name 'AGENTS.md' \
    -not -name 'SKILL.md' \
    -not -path $TUTORIALS_DIR'/.firebender/*' \
    -not -path $TUTORIALS_DIR'/.cursor/*' \
    -not -path $TUTORIALS_DIR'/.claude/*' \
    | xargs mdl --style $TUTORIALS_STYLE --ignore-front-matter \

echo "Success."
