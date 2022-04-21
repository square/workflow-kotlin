package com.squareup.sample.helloterminal.terminalworkflow

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.TextColor.ANSI
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.BLACK
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.BLUE
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.CYAN
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.DEFAULT
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.GREEN
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.MAGENTA
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.RED
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.WHITE
import com.squareup.sample.helloterminal.terminalworkflow.TerminalRendering.Color.YELLOW

/**
 * Represents the complete text to display on a terminal (is not additive), properties of that text,
 * as well as the event handler to handle keystrokes.
 *
 * The rendering type for [TerminalWorkflow].
 *
 * @param text The text to display to the terminal.
 * @param textColor Color of the text to display. All text will be the same color.
 * @param backgroundColor Color of the background of the terminal.
 */
data class TerminalRendering(
  val text: String,
  val textColor: Color = DEFAULT,
  val backgroundColor: Color = DEFAULT
) {
  enum class Color {
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    DEFAULT
  }
}

internal fun Color.toTextColor(): TextColor = when (this) {
  BLACK -> ANSI.BLACK
  RED -> ANSI.RED
  GREEN -> ANSI.GREEN
  YELLOW -> ANSI.YELLOW
  BLUE -> ANSI.BLUE
  MAGENTA -> ANSI.MAGENTA
  CYAN -> ANSI.CYAN
  WHITE -> ANSI.WHITE
  DEFAULT -> ANSI.DEFAULT
}
