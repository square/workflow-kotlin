# Configuring rules:
# https://github.com/markdownlint/markdownlint/blob/master/docs/creating_styles.md
# Rule list:
# https://github.com/markdownlint/markdownlint/blob/master/docs/RULES.md

# Need to explicitly run all rules, so the per-rule configs below aren't used as an allowlist.
all

# Increase default line length from 80 to 100.
rule 'MD013', :line_length => 100, :code_blocks => false, :tables => false

# Enable inline HTML.
exclude_rule 'MD033'

# Allow paragraphs that consiste entirely of emphasized text.
exclude_rule 'MD036'

# Allow trailing question marks in headers.
rule 'MD026', :punctuation => '.,;:!'

# Markdownlint can't handle mkdocs' code block tab syntax, so disable code block formatting.
exclude_rule 'MD040'
exclude_rule 'MD046'

# Don't care about blank lines surround fenced code blocks.
exclude_rule 'MD031'

# Allow raw URLs.
exclude_rule 'MD034'
