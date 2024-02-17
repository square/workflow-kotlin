package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewRegistry.Key
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

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

  @Test fun screen_withRegistry_works() {
    val fooFactory = TestFactory(FooScreen::class)
    val viewRegistry = ViewRegistry(fooFactory)
    val envScreen = FooScreen.withRegistry(viewRegistry)

    assertSame(
      fooFactory,
      envScreen.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)

    )

    assertNull(
      envScreen.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    )
  }

  @Test fun screen_withEnvironment_works() {
    val fooFactory = TestFactory(FooScreen::class)
    val viewRegistry = ViewRegistry(fooFactory)
    val envScreen = FooScreen.withEnvironment(
      EMPTY + viewRegistry + TestValue("foo")
    )

    assertSame(
      fooFactory,
      envScreen.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    )
    assertNull(
      envScreen.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    )
    assertEquals(
      TestValue("foo"),
      envScreen.environment[TestValue]
    )
  }

  @Test fun environmentScreen_withRegistry_merges() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withRegistry(ViewRegistry(fooFactory1, barFactory))
    val union = left.withRegistry(ViewRegistry(fooFactory2))

    assertSame(
      fooFactory2,
      union.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    )

    assertSame(
      barFactory,
      union.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen)
    )
  }

  @Test fun environmentScreen_withEnvironment_merges() {
    val fooFactory1 = TestFactory(FooScreen::class)
    val fooFactory2 = TestFactory(FooScreen::class)
    val barFactory = TestFactory(BarScreen::class)

    val left = FooScreen.withEnvironment(
      EMPTY + ViewRegistry(fooFactory1, barFactory) + TestValue("left")
    )

    val union = left.withEnvironment(
      EMPTY + ViewRegistry(fooFactory2) + TestValue("right")
    )

    assertSame(
      fooFactory2,
      union.environment[ViewRegistry].getFactoryFor<FooScreen, TestFactory<*>>(FooScreen)
    )
    assertSame(
      barFactory,
      union.environment[ViewRegistry].getFactoryFor<BarScreen, TestFactory<*>>(BarScreen),
    )
    assertEquals(TestValue("right"), union.environment[TestValue])
  }

  @Test fun keep_existing_instance_on_vacuous_merge() {
    val left = FooScreen.withEnvironment(EMPTY + TestValue("whatever"))
    assertSame(left, left.withEnvironment())
  }
}
