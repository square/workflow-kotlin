@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui

import android.content.Context
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
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
      fooScreen.toViewFactory(env)
        .startShowing(fooScreen, env, mock())
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

    screen.toViewFactory(env)
      .startShowing(screen, env, mock())
    assertThat(screen.viewFactory.built).isTrue()
    assertThat(screen.viewFactory.updated).isTrue()
  }

  @Test fun `buildView prefers registry entries to AndroidViewRendering`() {
    val env = ViewEnvironment.EMPTY + ViewRegistry(overrideViewRenderingFactory)

    val screen = MyAndroidScreen()
    screen.toViewFactory(env)
      .startShowing(screen, env, mock())
    assertThat(screen.viewFactory.built).isFalse()
    assertThat(screen.viewFactory.updated).isFalse()
    assertThat(overrideViewRenderingFactory.built).isTrue()
    assertThat(overrideViewRenderingFactory.updated).isTrue()
  }

  @Test fun `convenience wrapper is convenient`() {
    val env = ViewEnvironment.EMPTY + ViewRegistry()
    val screen = MyWrapper(MyAndroidScreen())

    screen.toViewFactory(env).startShowing(screen, env, mock())
    assertThat(screen.wrapped.viewFactory.built).isTrue()
    assertThat(screen.wrapped.viewFactory.updated).isTrue()
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  private class MyWrapper(
    val wrapped: MyAndroidScreen
  ) : AndroidScreen<MyWrapper> {
    override val viewFactory =
      fromCode<MyWrapper> { initialScreen, initialEnvironment, _, _ ->
        wrapped.viewFactory.toUnwrappingViewFactory<MyWrapper, MyAndroidScreen>(
          unwrap = { wrapped }
        ).startShowing(initialScreen, initialEnvironment, mock())
      }
  }

  private class TestViewFactory<T : Screen>(
    override val type: KClass<in T>
  ) : ScreenViewFactory<T> {
    var built = false
    var updated = false

    override fun buildView(
      initialRendering: T,
      initialEnvironment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): ScreenViewHolder<T> {
      built = true

      return ScreenViewHolder(initialEnvironment, mock()) { _, _ ->
        updated = true
      }
    }
  }

  private class MyAndroidScreen : AndroidScreen<MyAndroidScreen> {
    override val viewFactory = TestViewFactory(MyAndroidScreen::class)
  }

  private val overrideViewRenderingFactory = TestViewFactory(MyAndroidScreen::class)
}
