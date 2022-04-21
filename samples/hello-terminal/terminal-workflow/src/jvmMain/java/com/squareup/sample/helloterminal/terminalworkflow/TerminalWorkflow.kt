package com.squareup.sample.helloterminal.terminalworkflow

import com.squareup.workflow1.Workflow

typealias ExitCode = Int

/**
 * A [Workflow] that can interact with a text terminal.
 *
 * To run one of these workflows, pass it to [TerminalWorkflowRunner.run].
 */
interface TerminalWorkflow : Workflow<TerminalProps, ExitCode, TerminalRendering>
