# This is mostly a copy of .markdownlint.rb, with some settings adjusted to make working
# with tutorials easier.

# Need to explicitly run all rules, so the per-rule configs below aren't used as an allowlist.
all

# Disable line length validation, tutorial files are easier to work with longer lines.
exclude_rule 'MD013'

# The rule for blank lines around lists doesn't recognize nested list items as part
# of the same list, so just disable the whole rule.
exclude_rule 'MD032'

# Enable inline HTML.
exclude_rule 'MD033'

# Allow paragraphs that consist entirely of emphasized text.
exclude_rule 'MD036'

# Allow trailing question marks in headers.
rule 'MD026', :punctuation => '.,;:!'

# Don't care about blank lines surround fenced code blocks.
exclude_rule 'MD031'

# Allow raw URLs.
exclude_rule 'MD034'
