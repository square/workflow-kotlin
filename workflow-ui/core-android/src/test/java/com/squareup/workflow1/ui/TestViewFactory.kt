package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal fun <R : Any> ViewRegistry.buildView(rendering: R): View =
  buildView(rendering, ViewEnvironment(mapOf(ViewRegistry to this)), mock<Context>())

@OptIn(WorkflowUiExperimentalApi::class)
internal class TestViewFactory<R : Any>(override val type: KClass<R>) : ViewFactory<R> {
  var called = false

  override fun buildView(
    initialRendering: R,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    called = true
    return mock {
      on {
        getTag(eq(com.squareup.workflow1.ui.R.id.view_show_rendering_function))
      } doReturn (ShowRenderingTag(initialRendering, initialViewEnvironment, { _, _ -> }))
    }
  }
}
