package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.nhaarman.mockito_kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.fail

@OptIn(WorkflowUiExperimentalApi::class)
fun <R : Any> ViewRegistry.buildView(rendering: R): View =
  buildView(rendering, ViewEnvironment(this), mock<Context>())

@OptIn(WorkflowUiExperimentalApi::class)
class TestViewFactory<R : Any>(override val type: KClass<R>) : ViewFactory<R> {
  override fun buildView(
    initialRendering: R,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    fail()
  }
}
