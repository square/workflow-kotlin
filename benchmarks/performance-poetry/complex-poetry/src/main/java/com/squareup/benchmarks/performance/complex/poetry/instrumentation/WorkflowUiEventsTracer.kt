package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import androidx.tracing.Trace
import com.squareup.sample.poetry.PoemListScreen
import com.squareup.sample.poetry.StanzaListScreen
import com.squareup.sample.poetry.StanzaScreen

/**
 * Captures an event callback for any Rendering produced from a Workflow.
 */
data class WorkflowUiEvent(
  val callback: String,
  val screen: String
)

/**
 * Start traces for active events and then end them whenever the Rendering is produced.
 *
 * Singleton holds the state for the 'active' events between any Renderings as well as the total
 * counts for those type of events so that we can differentiate each one throughout the scenario.
 */
object WorkflowUiEventsTracer {

  private val activeEvents: MutableList<String> = mutableListOf()
  private val eventCounts: MutableMap<String, Int> = mutableMapOf()

  fun startTraceForEvent(event: WorkflowUiEvent) {
    val tag = synchronized(activeEvents) {
      val eventString = "${event.screen}-${event.callback}"
      eventCounts[eventString] = eventCounts.getOrDefault(eventString, 0) + 1
      val count = eventCounts[eventString]
      val uniqueEventTag = "$eventString-$count "
      activeEvents.add(uniqueEventTag)
      uniqueEventTag
    }
    Trace.beginAsyncSection(
      tag,
      EVENT_COOKIE
    )
  }

  fun endTracesForActiveEventsAndClear() {
    val copy = synchronized(activeEvents) {
      val cache = mutableListOf<String>()
      cache.addAll(activeEvents)
      activeEvents.clear()
      cache
    }
    copy.forEach { uniqueEventTag ->
      Trace.endAsyncSection(
        uniqueEventTag,
        EVENT_COOKIE
      )
    }
  }

  fun reset() {
    activeEvents.clear()
    eventCounts.clear()
  }

  private const val EVENT_COOKIE = 12345
}

fun StanzaScreen.trace(): StanzaScreen {
  return copy(
    onGoUp = {
      WorkflowUiEventsTracer.startTraceForEvent(WorkflowUiEvent("onGoUp", "StanzaScreen"))
      onGoUp()
    },
    onGoBack = {
      WorkflowUiEventsTracer.startTraceForEvent(WorkflowUiEvent("onGoBack", "StanzaScreen"))
      onGoBack?.let { it() }
    },
    onGoForth = {
      WorkflowUiEventsTracer.startTraceForEvent(WorkflowUiEvent("onGoForth", "StanzaScreen"))
      onGoForth?.let { it() }
    }
  )
}

fun StanzaListScreen.trace(): StanzaListScreen {
  return copy(
    onExit = {
      WorkflowUiEventsTracer.startTraceForEvent(WorkflowUiEvent("onExit", "StanzaListScreen"))
      onExit()
    },
    onStanzaSelected = { selection: Int ->
      WorkflowUiEventsTracer.startTraceForEvent(
        WorkflowUiEvent(
          "onStanzaSelected($selection)",
          "StanzaListScreen"
        )
      )
      onStanzaSelected(selection)
    }
  )
}

fun PoemListScreen.trace(): PoemListScreen {
  return copy(
    onPoemSelected = { selection: Int ->
      WorkflowUiEventsTracer.startTraceForEvent(
        WorkflowUiEvent(
          "onPoemSelected($selection)",
          "PoemListScreen"
        )
      )
      onPoemSelected(selection)
    }
  )
}
