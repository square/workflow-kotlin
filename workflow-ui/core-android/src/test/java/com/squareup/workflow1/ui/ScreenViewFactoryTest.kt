@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import org.junit.Test
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
      fooScreen.toView(env, mock())
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

    screen.toView(env, mock())
    assertThat(screen.viewFactory.built).isTrue()
    assertThat(screen.viewFactory.updated).isTrue()
  }

  @Test fun `buildView prefers registry entries to AndroidViewRendering`() {
    val env = ViewEnvironment.EMPTY + ViewRegistry(overrideViewRenderingFactory)

    val screen = MyAndroidScreen()
    screen.toView(env, mock())
    assertThat(screen.viewFactory.built).isFalse()
    assertThat(screen.viewFactory.updated).isFalse()
    assertThat(overrideViewRenderingFactory.built).isTrue()
    assertThat(overrideViewRenderingFactory.updated).isTrue()
  }

  private class TestViewFactory<T : Screen>(
    override val type: KClass<in T>
  ) : ScreenViewFactory<T> {
    var built = false
    var updated = false

    override fun buildView(
      environment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): View {
      built = true

      return mock()
    }

    override fun updateView(
      view: View,
      rendering: T,
      environment: ViewEnvironment
    ) {
      updated = true
    }
  }

  private class MyAndroidScreen : AndroidScreen<MyAndroidScreen> {
    override val viewFactory = TestViewFactory(MyAndroidScreen::class)
  }

  private val overrideViewRenderingFactory = TestViewFactory(MyAndroidScreen::class)
}
