package com.squareup.workflow1.traceviewer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.difflib.text.DiffRow.Tag
import com.github.difflib.text.DiffRowGenerator

/**
 * Generates a field-level word-diff for each node's states.
 *
 */
fun computeAnnotatedDiff(
  past: String,
  current: String
): AnnotatedString {
  val diffGenerator = DiffRowGenerator.create()
    .showInlineDiffs(true)
    .inlineDiffByWord(true)
    .mergeOriginalRevised(true)
    .oldTag { f -> "--" }
    .newTag { f -> "++" }
    .build()

  val pastName = extractTypeName(past)
  val currentName = extractTypeName(current)
  val pastFields = getFieldsAsList(past)
  val currentFields = getFieldsAsList(current)
  val diffRows = diffGenerator.generateDiffRows(pastFields, currentFields)

  var existsDiff = false
  return buildAnnotatedString {
    // A full change in the type means all internal data will be changed, so it's easier to just
    //generalize and show the diff in the type's name
    if (pastName != currentName) {
      buildString(
        style = DiffStyles.DELETE,
        text = "$pastName(...)",
        builder = this
      )
      // pushStyle(DiffStyles.DELETE)
      // append("$pastName(...)")
      // pop()
      append(" → ")
      buildString(
        style = DiffStyles.INSERT,
        text = "$currentName(...)",
        builder = this
      )
      return@buildAnnotatedString
    }

    diffRows.forEach { row ->
      val tag = row.tag!!
      // The 'mergeOriginalRevised' flag changes the semantics of the data, but the API still returns
      // the same components
      val fullDiff = row.oldLine

      /*
      Tag.INSERT and Tag.DELETE only happens when there is a difference in number of rows, i.e.:
        Tag(["a"],["a","b"]) == INSERT
      and
        Tag(["a","b"],["a"]) == DELETE
      but
        Tag([""],["a"]) == CHANGE
       */
      when (tag) {
        Tag.CHANGE -> {
          existsDiff = true
          parseChangedDiff(fullDiff).forEach { (style, text) ->
            buildString(
              style = style,
              text = text,
              builder = this
            )
          }
          append("\n\n")
        }

        Tag.INSERT -> {
          existsDiff = true
          buildString(
            text = fullDiff.replace("++", ""),
            style = DiffStyles.INSERT,
            builder = this
          )
          append("\n\n")
        }

        Tag.DELETE -> {
          existsDiff = true
          buildString(
            text = fullDiff.replace("--", ""),
            style = DiffStyles.DELETE,
            builder = this
          )
          append("\n\n")
        }

        Tag.EQUAL -> {
          // NoOp
        }
      }
    }

    if (!existsDiff) {
      buildString(
        style = DiffStyles.NO_CHANGE,
        text = "No Diff",
        builder = this
      )
    }
  }
}

/**
 * Parses the full diff within Tag.CHANGED to give back a list of operations to perform
 */
private fun parseChangedDiff(fullDiff: String): List<Pair<SpanStyle, String>> {
  val operations: MutableList<Pair<SpanStyle, String>> = mutableListOf()
  var i = 0
  while (i < fullDiff.length) {
    when {
      fullDiff.startsWith("--", i) -> {
        val end = fullDiff.indexOf("--", i + 2)
        if (end != -1) {
          val removed = fullDiff.substring(i + 2, end)
          operations.add(DiffStyles.DELETE to removed)
          i = end + 2
        }
      }

      fullDiff.startsWith("++", i) -> {
        val end = fullDiff.indexOf("++", i + 2)
        if (end != -1) {
          val added = fullDiff.substring(i + 2, end)
          operations.add(DiffStyles.INSERT to added)
          i = end + 2
        }
      }

      else -> {
        val nextTagStart = listOf(
          fullDiff.indexOf("--", i),
          fullDiff.indexOf("++", i)
        ).filter { it >= 0 }.minOrNull() ?: fullDiff.length
        operations.add(DiffStyles.UNCHANGED to fullDiff.substring(i, nextTagStart))
        i = nextTagStart
      }
    }
  }

  return operations
}

object DiffStyles {
  val DELETE = SpanStyle(background = Color.Red.copy(alpha = 0.3f))
  val INSERT = SpanStyle(background = Color.Green.copy(alpha = 0.3f))
  val NO_CHANGE = SpanStyle(background = Color.LightGray)
  val UNCHANGED = SpanStyle()
}

internal fun buildString(
  style: SpanStyle,
  text: String,
  builder: AnnotatedString.Builder
) {
  builder.pushStyle(style)
  builder.append(text)
  builder.pop()
}

/**
 * Pull out each "key=value" pair within the field data by looking for a comma. Since plenty of data
 * include nesting, doing .split or simple regex won't suffice.
 *
 * Manually iterates through the fields and changes the depth of the current comma accordingly
 */
private fun getFieldsAsList(field: String): List<String> {
  val fields = mutableListOf<String>()
  val currentField = StringBuilder()
  var depth = 0
  // We skip past the field's Type's name
  var i = field.indexOf('(') + 1

  while (i < field.length) {
    val char = field[i]
    when (char) {
      '(', '[', '{' -> {
        depth++
        currentField.append(char)
      }

      ')', ']', '}' -> {
        depth--
        currentField.append(char)
      }

      ',' -> {
        if (depth == 0) { // end of key=value pair
          fields += currentField.toString().trim()
          currentField.clear()
          i++ // skip space, e.g. "key=value, key2=value2, etc..."
        } else { // nested list
          currentField.append(char)
        }
      }

      else -> currentField.append(char)
    }
    i++
  }

  // Just append whatever is left, since there are no trailing commas
  if (currentField.isNotBlank()) fields += currentField.toString().trim()
  return fields
}

private fun extractTypeName(field: String): String {
  val stateRegex = Regex("""^(\w+)\(""")
  // If regex doesn't match, that means it's likely "kotlin.Unit" or "0"
  return stateRegex.find(field)?.groupValues?.get(1) ?: field
}
