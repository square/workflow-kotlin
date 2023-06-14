package com.squareup.workflow1.buildsrc

import com.github.difflib.text.DiffRow.Tag
import com.github.difflib.text.DiffRowGenerator
import com.squareup.workflow1.buildsrc.Color.Companion.colorized
import com.squareup.workflow1.buildsrc.Color.LIGHT_GREEN
import com.squareup.workflow1.buildsrc.Color.LIGHT_YELLOW

fun diffString(oldStr: String, newStr: String): String {

  return buildString {

    val rows = DiffRowGenerator.create()
      .showInlineDiffs(true)
      .inlineDiffByWord(true)
      .oldTag { _: Boolean? -> "" }
      .newTag { _: Boolean? -> "" }
      .build()
      .generateDiffRows(oldStr.lines(), newStr.lines())

    val linePadding = rows.size.toString().length + 1

    rows.forEachIndexed { line, diffRow ->
      if (diffRow.tag != Tag.EQUAL) {
        append("line ${line.inc().toString().padEnd(linePadding)} ")
      }

      if (diffRow.tag == Tag.CHANGE || diffRow.tag == Tag.DELETE) {
        appendLine("--  ${diffRow.oldLine}".colorized(LIGHT_YELLOW))
      }
      if (diffRow.tag == Tag.CHANGE) {
        append("      " + " ".repeat(linePadding))
      }
      if (diffRow.tag == Tag.CHANGE || diffRow.tag == Tag.INSERT) {
        appendLine("++  ${diffRow.newLine}".colorized(LIGHT_GREEN))
      }
    }
  }
}

@Suppress("MagicNumber")
internal enum class Color(val code: Int) {
  LIGHT_GREEN(92),
  LIGHT_YELLOW(93);

  companion object {

    private val supported = "win" !in System.getProperty("os.name").lowercase()

    fun String.colorized(color: Color) = if (supported) {
      "\u001B[${color.code}m$this\u001B[0m"
    } else {
      this
    }
  }
}
