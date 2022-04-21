package com.squareup.sample.helloterminal

import com.squareup.sample.helloterminal.terminalworkflow.TerminalWorkflowRunner
import kotlin.system.exitProcess

fun main() {
  val runner = TerminalWorkflowRunner()
  val exitCode = runner.run(HelloTerminalWorkflow())
  println("Workflow exited with code $exitCode")
  exitProcess(exitCode)
}
