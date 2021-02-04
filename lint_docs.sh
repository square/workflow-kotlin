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
find . \
    -name '*.md' \
    -not -name 'CHANGELOG.md' \
    -not -path './.github/*' \
    -not -path $TUTORIALS_DIR/'*' \
    -not -path './compose/*' \
    | xargs mdl --style $STYLE --ignore-front-matter \

find $TUTORIALS_DIR \
    -name '*.md' \
    | xargs mdl --style $TUTORIALS_STYLE --ignore-front-matter \

echo "Success."
