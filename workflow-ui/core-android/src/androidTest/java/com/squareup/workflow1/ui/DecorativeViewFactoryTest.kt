@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class DecorativeViewFactoryTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun viewStarter_is_only_call_to_showRendering() {
    val events = mutableListOf<String>()

    val innerViewFactory = object : ViewFactory<InnerRendering> {
      override val type = InnerRendering::class
      override fun buildView(
        initialRendering: InnerRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View = InnerView(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          events += "inner showRendering $rendering"
        }
      }
    }

    val envString = object : ViewEnvironmentKey<String>(String::class) {
      override val default: String get() = "Not set"
    }

    val outerViewFactory = DecorativeViewFactory(
      type = OuterRendering::class,
      map = { outer, env ->
        val enhancedEnv = env + (envString to "Updated environment")
        Pair(outer.wrapped, enhancedEnv)
      },
      viewStarter = { view, doStart ->
        events += "viewStarter ${view.getRendering<Any>()} ${view.environment!![envString]}"
        doStart()
        events += "exit viewStarter"
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment.EMPTY + viewRegistry

    viewRegistry.buildView(
      OuterRendering("outer", InnerRendering("inner")),
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

    val innerViewFactory = object : ViewFactory<InnerRendering> {
      override val type = InnerRendering::class
      override fun buildView(
        initialRendering: InnerRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View = InnerView(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          events += "inner showRendering $rendering"
        }
      }
    }
    val outerViewFactory = DecorativeViewFactory(
      type = OuterRendering::class,
      map = { outer -> outer.wrapped },
      doShowRendering = { _, innerShowRendering, outerRendering, env ->
        events += "doShowRendering $outerRendering"
        innerShowRendering(outerRendering.wrapped, env)
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment.EMPTY + viewRegistry

    viewRegistry.buildView(
      OuterRendering("outer", InnerRendering("inner")),
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

    val innerViewFactory = object : ViewFactory<InnerRendering> {
      override val type = InnerRendering::class
      override fun buildView(
        initialRendering: InnerRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View = InnerView(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          events += "inner showRendering $rendering"
        }
      }
    }

    val envString = object : ViewEnvironmentKey<String>(String::class) {
      override val default: String get() = "Not set"
    }

    val outerViewFactory = DecorativeViewFactory(
      type = OuterRendering::class,
      map = { outer, env ->
        val enhancedEnv = env + (
          envString to "Outer Updated environment" +
            " SHOULD NOT SEE THIS! It will be clobbered by WayOutRendering"
          )
        Pair(outer.wrapped, enhancedEnv)
      },
      viewStarter = { view, doStart ->
        events += "outer viewStarter ${view.getRendering<Any>()} ${view.environment!![envString]}"
        doStart()
        events += "exit outer viewStarter"
      }
    )

    val wayOutViewFactory = DecorativeViewFactory(
      type = WayOutRendering::class,
      map = { wayOut, env ->
        val enhancedEnv = env + (envString to "Way Out Updated environment triumphs over all")
        Pair(wayOut.wrapped, enhancedEnv)
      },
      viewStarter = { view, doStart ->
        events += "way out viewStarter ${view.getRendering<Any>()} ${view.environment!![envString]}"
        doStart()
        events += "exit way out viewStarter"
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory, wayOutViewFactory)
    val viewEnvironment = ViewEnvironment.EMPTY + viewRegistry

    viewRegistry.buildView(
      WayOutRendering("way out", OuterRendering("outer", InnerRendering("inner"))),
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

    val innerViewFactory = object : ViewFactory<InnerRendering> {
      override val type = InnerRendering::class
      override fun buildView(
        initialRendering: InnerRendering,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup?
      ): View = InnerView(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          events += "inner showRendering $rendering"
        }
      }
    }
    val outerViewFactory = DecorativeViewFactory(
      type = OuterRendering::class,
      map = { outer -> outer.wrapped },
      doShowRendering = { _, innerShowRendering, outerRendering, env ->
        events += "doShowRendering $outerRendering"
        innerShowRendering(outerRendering.wrapped, env)
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory, outerViewFactory)
    val viewEnvironment = ViewEnvironment.EMPTY + viewRegistry

    val view = viewRegistry.buildView(
      OuterRendering("out1", InnerRendering("in1")),
      viewEnvironment,
      instrumentation.context
    ).apply { start() }
    events.clear()

    view.showRendering(OuterRendering("out2", InnerRendering("in2")), viewEnvironment)

    assertThat(events).containsExactly(
      "doShowRendering OuterRendering(outerData=out2, wrapped=InnerRendering(innerData=in2))",
      "inner showRendering InnerRendering(innerData=in2)"
    )
  }

  private data class InnerRendering(val innerData: String)
  private data class OuterRendering(
    val outerData: String,
    val wrapped: InnerRendering
  )

  private data class WayOutRendering(
    val wayOutData: String,
    val wrapped: OuterRendering
  )

  private class InnerView(context: Context) : View(context)
}
