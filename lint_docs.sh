#!/bin/bash
#
# Copyright 2019 Square Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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
    | xargs mdl --style $STYLE --ignore-front-matter \

find $TUTORIALS_DIR \
    -name '*.md' \
    | xargs mdl --style $TUTORIALS_STYLE --ignore-front-matter \

echo "Success."
