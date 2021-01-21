package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
class DecorativeViewFactoryTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun initView_called_before_showRendering() {
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
      initView = { outerRendering, _ ->
        events += "initView $outerRendering"
      }
    )
    val viewRegistry = ViewRegistry(innerViewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    outerViewFactory.buildView(
      OuterRendering("outer", InnerRendering("inner")),
      viewEnvironment,
      instrumentation.context
    )

    // Note that showRendering is called twice. Technically this behavior is not incorrect, although
    // it's not necessary. Fix coming soon.
    assertThat(events).containsExactly(
      "inner showRendering InnerRendering(innerData=inner)",
      "initView OuterRendering(outerData=outer, wrapped=InnerRendering(innerData=inner))",
      "inner showRendering InnerRendering(innerData=inner)"
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

    // Note that showRendering is called twice. Technically this behavior is not incorrect, although
    // it's not necessary. Fix coming soon.
    assertThat(events).containsExactly(
      "inner showRendering InnerRendering(innerData=inner)",
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
