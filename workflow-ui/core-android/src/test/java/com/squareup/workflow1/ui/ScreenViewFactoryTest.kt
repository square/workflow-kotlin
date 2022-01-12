@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

internal class ScreenViewFactoryTest {

  @OptIn(WorkflowUiExperimentalApi::class)
  @Test fun missingBindingMessage_isUseful() {
    val emptyReg = object : ViewRegistry {
      override val keys: Set<KClass<*>> = emptySet()
      override fun <RenderingT : Any> getEntryFor(
        renderingType: KClass<out RenderingT>
      ): Entry<RenderingT>? = null
    }
    val env = ViewEnvironment.EMPTY + emptyReg

    val fooScreen = object : Screen {
      override fun toString() = "FooScreen"
    }

    val error = assertFailsWith<IllegalArgumentException> {
      fooScreen.buildView(env, mock())
    }
    assertThat(error.message).isEqualTo(
      "A ScreenViewFactory should have been registered to display " +
        "FooScreen, or that class should implement AndroidScreen. " +
        "Instead found null."
    )
  }

  @Test fun `buildView honors AndroidScreen`() {
    val env = ViewEnvironment.EMPTY + ViewRegistry()
    val screen = MyAndroidScreen()

    screen.buildView(env, mock())
    assertThat(screen.viewFactory.called).isTrue()
  }

  @Test fun `buildView prefers registry entries to AndroidViewRendering`() {
    val env = ViewEnvironment.EMPTY + ViewRegistry(overrideViewRenderingFactory)

    val screen = MyAndroidScreen()
    screen.buildView(env, mock())
    assertThat(screen.viewFactory.called).isFalse()
    assertThat(overrideViewRenderingFactory.called).isTrue()
  }

  private class TestViewFactory<T : Screen>(
    override val type: KClass<in T>
  ) : ScreenViewFactory<T> {
    var called = false

    override fun buildView(
      initialRendering: T,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      called = true

      return mock {
        on {
          getTag(eq(com.squareup.workflow1.ui.R.id.workflow_ui_view_state))
        } doReturn (WorkflowViewState.New(initialRendering, initialViewEnvironment, { _, _ -> }))
      }
    }
  }

  private class MyAndroidScreen : AndroidScreen<MyAndroidScreen> {
    override val viewFactory = TestViewFactory(MyAndroidScreen::class)
  }

  private val overrideViewRenderingFactory = TestViewFactory(MyAndroidScreen::class)
}
