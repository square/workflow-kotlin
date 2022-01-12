package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.get
import com.squareup.workflow1.ui.plus
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class EnvironmentScreenTest {
  private class TestFactory<T : Any>(
    override val type: KClass<in T>
  ) : ViewRegistry.Entry<T>

  private data class TestValue(val value: String) {
    companion object : ViewEnvironmentKey<TestValue>(TestValue::class) {
      override val default: TestValue get() = error("Set a default")
    }
  }

  private operator fun ViewEnvironment.plus(other: TestValue): ViewEnvironment {
    return this + (TestValue to other)
  }

  private object FooScreen : Screen
  private object BarScreen : Screen

  @Test fun `Screen withRegistry works`() {
    val fooFactory = TestFactory(FooScreen::class)
    val viewRegistry = ViewRegistry(fooFactory)
    val envScreen = FooScreen.withRegistry(viewRegistry)

    assertThat(envScreen.viewEnvironment[ViewRegistry][FooScreen::class])
      .isSameInstanceAs(fooFactory)

    assertThat(envScreen.viewEnvironment[ViewRegistry][BarScreen::class])
      .isNull()
  }

  @Test fun `Screen withEnvironment works`() {
    val fooFactory = TestFactory(FooScreen::class)
    val viewRegistry = ViewRegistry(fooFactory)
    val envScreen = FooScreen.withEnvironment(
      ViewEnvironment.EMPTY + viewRegistry + TestValue("foo")
    )

    assertThat(envScreen.viewEnvironment[ViewRegistry][FooScreen::class])
      .isSameInstanceAs(fooFactory)
    assertThat(envScreen.viewEnvironment[ViewRegistry][BarScreen::class])
      .isNull()
    assertThat(envScreen.viewEnvironment[TestValue])
      .isEqualTo(TestValue("foo"))
  }

  @Test fun `EnvironmentScreen withRegistry merges`() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withRegistry(ViewRegistry(fooFactory1, barFactory))
    val union = left.withRegistry(ViewRegistry(fooFactory2))

    assertThat(union.viewEnvironment[ViewRegistry][FooScreen::class])
      .isSameInstanceAs(fooFactory2)

    assertThat(union.viewEnvironment[ViewRegistry][BarScreen::class])
      .isSameInstanceAs(barFactory)
  }

  @Test fun `EnvironmentScreen withEnvironment merges`() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withEnvironment(
      ViewEnvironment.EMPTY + ViewRegistry(fooFactory1, barFactory) + TestValue("left")
    )

    val union = left.withEnvironment(
      ViewEnvironment.EMPTY + ViewRegistry(fooFactory2) + TestValue("right")
    )

    assertThat(union.viewEnvironment[ViewRegistry][FooScreen::class])
      .isSameInstanceAs(fooFactory2)
    assertThat(union.viewEnvironment[ViewRegistry][BarScreen::class])
      .isSameInstanceAs(barFactory)
    assertThat(union.viewEnvironment[TestValue])
      .isEqualTo(TestValue("right"))
  }
}
