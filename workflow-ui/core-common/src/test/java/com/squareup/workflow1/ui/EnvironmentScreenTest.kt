package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry.Key
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class EnvironmentScreenTest {
  private class TestFactory<T : Any>(
    type: KClass<in T>
  ) : ViewRegistry.Entry<T> {
    override val key = Key(type, TestFactory::class)
  }

  private data class TestValue(val value: String) {
    companion object : ViewEnvironmentKey<TestValue>() {
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

    assertThat(
      envScreen.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    ).isSameInstanceAs(fooFactory)

    assertThat(
      envScreen.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    ).isNull()
  }

  @Test fun `Screen withEnvironment works`() {
    val fooFactory = TestFactory(FooScreen::class)
    val viewRegistry = ViewRegistry(fooFactory)
    val envScreen = FooScreen.withEnvironment(
      EMPTY + viewRegistry + TestValue("foo")
    )

    assertThat(
      envScreen.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    ).isSameInstanceAs(fooFactory)
    assertThat(
      envScreen.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    ).isNull()
    assertThat(envScreen.environment[TestValue])
      .isEqualTo(TestValue("foo"))
  }

  @Test fun `EnvironmentScreen withRegistry merges`() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withRegistry(ViewRegistry(fooFactory1, barFactory))
    val union = left.withRegistry(ViewRegistry(fooFactory2))

    assertThat(
      union.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    ).isSameInstanceAs(fooFactory2)

    assertThat(
      union.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    ).isSameInstanceAs(barFactory)
  }

  @Test fun `EnvironmentScreen withEnvironment merges`() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withEnvironment(
      EMPTY + ViewRegistry(fooFactory1, barFactory) + TestValue("left")
    )

    val union = left.withEnvironment(
      EMPTY + ViewRegistry(fooFactory2) + TestValue("right")
    )

    assertThat(
      union.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    ).isSameInstanceAs(fooFactory2)
    assertThat(
      union.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    ).isSameInstanceAs(barFactory)
    assertThat(union.environment[TestValue])
      .isEqualTo(TestValue("right"))
  }

  @Test fun `keep existing instance on vacuous merge`() {
    val left = FooScreen.withEnvironment(EMPTY + TestValue("whatever"))
    assertThat(left.withEnvironment()).isSameInstanceAs(left)
  }
}
