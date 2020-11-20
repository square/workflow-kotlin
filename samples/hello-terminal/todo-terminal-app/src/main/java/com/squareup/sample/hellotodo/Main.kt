package com.squareup.sample.hellotodo

import com.squareup.sample.helloterminal.terminalworkflow.TerminalWorkflowRunner
import kotlin.system.exitProcess

fun main() {
  val runner = TerminalWorkflowRunner()
  val exitCode = runner.run(TodoWorkflow())
  println("Workflow exited with code $exitCode")
  exitProcess(exitCode)
}
