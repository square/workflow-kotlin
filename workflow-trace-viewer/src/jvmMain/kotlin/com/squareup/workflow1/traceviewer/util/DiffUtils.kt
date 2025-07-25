package com.squareup.workflow1.traceviewer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator

/**
 * Utility class to generate colored diff between two texts
 */

fun computeAnnotatedDiff(
  past: String,
  current: String
): AnnotatedString {


  val diffGenerator = DiffRowGenerator.create()
    .showInlineDiffs(true)
    .inlineDiffByWord(true)
    // .replaceOriginalLinefeedInChangesWithSpaces(true)
    .oldTag { f -> "--" }
    .newTag { f -> "++" }
    .build()

  val pastName = extractTypeName(past)
  val pastFields = getFieldsAsList(past)
  val currentName = extractTypeName(current)
  val currentFields = getFieldsAsList(current)
  print(past + "\n\n")
  println(pastFields)
  // println(diffGenerator.generateDiffRows(pastFields, currentFields))
  var existsDiff = false
  return buildAnnotatedString {
    if (pastName != currentName) {
      append("\n")
      pushStyle(SpanStyle(background = Color.Red.copy(alpha = 0.3f)))
      append("$pastName(...)")
      pop()
      append(" → ")
      pushStyle(SpanStyle(background = Color.Green.copy(alpha = 0.3f)))
      append("$currentName(...)")
      pop()
    }

    /*
     zip shortens both to the shortest one, so
     a) if past > current, then we pushStyle(delete) for the rest of past
     b) if current > past, then we pushStyle(add) for the rest of current
     */
    // pastFields.zip(currentFields).forEach { (pastField, currentField) ->
    //   val diff = diffGenerator.generateDiffRows(pastField, currentField)
    //   val tag = diff[0]
    //   if (tag == DiffRow.Tag.CHANGE) {
    //
    //   }
    // }
  }
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
