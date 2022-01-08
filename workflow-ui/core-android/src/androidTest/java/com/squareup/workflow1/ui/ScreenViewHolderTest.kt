package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class ScreenViewHolderTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun viewStarter_is_only_call_to_showRendering() {
    val events = mutableListOf<String>()

    val innerViewFactory =
      ScreenViewFactory.of<InnerRendering> { initialRendering, initialViewEnvironment, context, _ ->
        ScreenViewHolder(initialRendering, initialViewEnvironment, InnerView(context)) { s, _ ->
          events += "inner showRendering $s"
        }
      }

    val envString = object : ViewEnvironmentKey<String>(String::class) {
      override val default: String get() = "Not set"
    }

    val outerViewFactory = ScreenViewFactory
      .of<OuterRendering> { initialRendering, initialViewEnvironment, context, container ->
        initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
          .acceptRenderings(initialRendering) { initialRendering.wrapped }
          .withStarter { viewHolder, doStart ->
            events += "viewStarter ${viewHolder.screen} ${viewHolder.environment[envString]}"
            doStart()
            events += "exit viewStarter"
          }
      }

    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    OuterRendering("outer", InnerRendering("inner")).buildView(
      viewEnvironment,
      instrumentation.context
    ).start()

    assertThat(events).containsExactly(
      "viewStarter OuterRendering(outerData=outer, wrapped=InnerRendering(innerData=inner)) " +
        "Updated environment",
      "inner showRendering InnerRendering(innerData=inner)",
      "exit viewStarter"
    )
  }

  @Test fun initial_doShowRendering_calls_wrapped_showRendering() {
    val events = mutableListOf<String>()

    val innerViewFactory =
      ScreenViewFactory.of<InnerRendering> { initialRendering, initialViewEnvironment, context, _ ->
        ScreenViewHolder(initialRendering, initialViewEnvironment, InnerView(context)) { s, _ ->
          events += "inner showRendering $s"
        }
      }

    val outerViewFactory = ScreenViewFactory
      .of<OuterRendering> { initialRendering, initialViewEnvironment, context, container ->
        initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
          .acceptRenderings(initialRendering) { it.wrapped }
          .withShowScreen { outerRendering, viewEnvironment ->
            events += "doShowRendering $outerRendering"
            showScreen(outerRendering, viewEnvironment)
          }
      }

    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    OuterRendering("outer", InnerRendering("inner")).buildView(
      viewEnvironment,
      instrumentation.context
    ).start()

    assertThat(events).containsExactly(
      "doShowRendering OuterRendering(outerData=outer, wrapped=InnerRendering(innerData=inner))",
      "inner showRendering InnerRendering(innerData=inner)"
    )
  }

  // https://github.com/square/workflow-kotlin/issues/597
  @Test fun double_wrapping_only_calls_showRendering_once() {
    val events = mutableListOf<String>()

    val innerViewFactory = ScreenViewFactory
      .of<InnerRendering> { initialRendering, initialViewEnvironment, context, _ ->
        ScreenViewHolder(initialRendering, initialViewEnvironment, InnerView(context)) { s, _ ->
          events += "inner showRendering $s"
        }
      }

    val envString = object : ViewEnvironmentKey<String>(String::class) {
      override val default: String get() = "Not set"
    }

    val outerViewFactory =
      ScreenViewFactory.of<OuterRendering> { initialRendering, initialViewEnvironment, context, container ->
        initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
          .acceptRenderings(initialRendering) { it.wrapped }
          .updateEnvironment { env ->
            env + (envString to "Outer Updated environment" +
              " SHOULD NOT SEE THIS! It will be clobbered by WayOutRendering")
          }
          .withStarter { viewHolder, doStart ->
            events += "outer viewStarter ${viewHolder.screen} ${viewHolder.environment[envString]}"
            doStart()
            events += "exit outer viewStarter"
          }
      }

    val wayOutViewFactory = ScreenViewFactory
      .of<WayOutRendering> { initialRendering, initialViewEnvironment, context, container ->
        initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
          .acceptRenderings(initialRendering) { it.wrapped }
          .updateEnvironment { env ->
            env + (envString to "Way Out Updated environment triumphs over all")
          }
          .withStarter { viewHolder, doStart ->
            events += "way out viewStarter ${viewHolder.screen} ${viewHolder.environment[envString]}"
            doStart()
            events += "exit way out viewStarter"
          }
      }

    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory, wayOutViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    WayOutRendering("way out", OuterRendering("outer", InnerRendering("inner"))).buildView(
      viewEnvironment,
      instrumentation.context
    ).start()

    assertThat(events).containsExactly(
      "way out viewStarter " +
        "WayOutRendering(wayOutData=way out, wrapped=" +
        "OuterRendering(outerData=outer, wrapped=" +
        "InnerRendering(innerData=inner))) " +
        "Way Out Updated environment triumphs over all",
      "outer viewStarter " +
        // Notice that both the initial rendering and the ViewEnvironment are stomped by
        // the outermost wrapper before inners are invoked. Could try to give
        // the inner wrapper access to the rendering it expected, but there are no
        // use cases and it trashes the API.
        "WayOutRendering(wayOutData=way out, wrapped=" +
        "OuterRendering(outerData=outer, wrapped=" +
        "InnerRendering(innerData=inner))) " +
        "Way Out Updated environment triumphs over all",
      "inner showRendering InnerRendering(innerData=inner)",
      "exit outer viewStarter",
      "exit way out viewStarter"
    )
  }

  @Test fun subsequent_showRendering_calls_wrapped_showRendering() {
    val events = mutableListOf<String>()

    val innerViewFactory =
      ScreenViewFactory.of<InnerRendering> { initialRendering, initialViewEnvironment, context, _ ->
        ScreenViewHolder(initialRendering, initialViewEnvironment, InnerView(context)) { s, _ ->
          events += "inner showRendering $s"
        }
      }

    val outerViewFactory = ScreenViewFactory
      .of<OuterRendering> { initialRendering, initialViewEnvironment, context, container ->
        initialRendering.wrapped.buildView(initialViewEnvironment, context, container)
          .acceptRenderings(initialRendering) { it.wrapped }
          .withShowScreen { outerRendering, viewEnvironment ->
            events += "doShowRendering $outerRendering"
            showScreen(outerRendering, viewEnvironment)
          }
      }

    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    val viewHolder = OuterRendering("out1", InnerRendering("in1")).buildView(
      viewEnvironment,
      instrumentation.context
    ).apply { start() }
    events.clear()

    viewHolder.showScreen(OuterRendering("out2", InnerRendering("in2")), viewEnvironment)

    assertThat(events).containsExactly(
      "doShowRendering OuterRendering(outerData=out2, wrapped=InnerRendering(innerData=in2))",
      "inner showRendering InnerRendering(innerData=in2)"
    )
  }

  private data class InnerRendering(val innerData: String) : Screen
  private data class OuterRendering(
    val outerData: String,
    val wrapped: InnerRendering
  ) : Screen

  private data class WayOutRendering(
    val wayOutData: String,
    val wrapped: OuterRendering
  ) : Screen

  private class InnerView(context: Context) : View(context)
}
