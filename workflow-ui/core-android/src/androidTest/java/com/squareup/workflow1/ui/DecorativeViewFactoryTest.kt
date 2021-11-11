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

  @Test fun initializeView_is_only_call_to_showRendering() {
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
      initializeView = {
        val outerRendering = getRendering<OuterRendering>()
        events += "initializeView $outerRendering ${environment!![envString]}"
        showFirstRendering()
        events += "exit initializeView"
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    outerViewFactory.buildView(
      OuterRendering("outer", InnerRendering("inner")),
      viewEnvironment,
      instrumentation.context
    )

    assertThat(events).containsExactly(
      "initializeView OuterRendering(outerData=outer, wrapped=InnerRendering(innerData=inner)) " +
        "Updated environment",
      "inner showRendering InnerRendering(innerData=inner)",
      "exit initializeView"
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
    val viewRegistry = ViewRegistry(innerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    outerViewFactory.buildView(
      OuterRendering("outer", InnerRendering("inner")),
      viewEnvironment,
      instrumentation.context
    )

    assertThat(events).containsExactly(
      "doShowRendering OuterRendering(outerData=outer, wrapped=InnerRendering(innerData=inner))",
      "inner showRendering InnerRendering(innerData=inner)"
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
    val viewRegistry = ViewRegistry(innerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    val view = outerViewFactory.buildView(
      OuterRendering("out1", InnerRendering("in1")),
      viewEnvironment,
      instrumentation.context
    )
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

  private class InnerView(context: Context) : View(context)
}
