package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackScreenTest {
  data class FooScreen<T>(val value: T) : Screen
  data class BarScreen<T>(val value: T) : Screen

  @Test fun `top  is last`() {
    assertThat(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)).top
    ).isEqualTo(
      FooScreen(4)
    )
  }

  @Test fun `backstack is all but top`() {
    assertThat(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)).backStack)
      .isEqualTo(listOf(FooScreen(1), FooScreen(2), FooScreen(3)))
  }

  @Test fun `get works`() {
    assertThat(
      BackStackScreen(FooScreen("able"), FooScreen("baker"), FooScreen("charlie"))[1]
    ).isEqualTo(
      FooScreen("baker")
    )
  }

  @Test fun `plus another stack`() {
    assertThat(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)) + BackStackScreen(
        FooScreen(8),
        FooScreen(9),
        FooScreen(0)
      )
    )
      .isEqualTo(
        BackStackScreen(
          FooScreen(1),
          FooScreen(2),
          FooScreen(3),
          FooScreen(8),
          FooScreen(9),
          FooScreen(0)
        )
      )
  }

  @Test fun `unequal by order`() {
    assertThat(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)))
      .isNotEqualTo(BackStackScreen(FooScreen(3), FooScreen(2), FooScreen(1)))
  }

  @Test fun `equal have matching hash`() {
    assertThat(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode())
      .isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode())
  }

  @Test fun `unequal have mismatching hash`() {
    assertThat(BackStackScreen(FooScreen(1), FooScreen(2)).hashCode())
      .isNotEqualTo(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode())
  }

  @Test fun `bottom and rest`() {
    assertThat(
      BackStackScreen.fromList(
        listOf(element = FooScreen(1)) + listOf(FooScreen(2), FooScreen(3), FooScreen(4))
      )
    ).isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)))
  }

  @Test fun singleton() {
    val stack = BackStackScreen(FooScreen("hi"))
    assertThat(stack.top).isEqualTo(FooScreen("hi"))
    assertThat(stack.frames).isEqualTo(listOf(FooScreen("hi")))
    assertThat(stack).isEqualTo(BackStackScreen(FooScreen("hi")))
  }

  @Test fun map() {
    assertThat(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).map {
        FooScreen(it.value * 2)
      }
    )
      .isEqualTo(BackStackScreen(FooScreen(2), FooScreen(4), FooScreen(6)))
  }

  @Test fun mapIndexed() {
    val source = BackStackScreen(FooScreen("able"), FooScreen("baker"), FooScreen("charlie"))
    assertThat(source.mapIndexed { index, frame -> FooScreen("$index: ${frame.value}") })
      .isEqualTo(
        BackStackScreen(FooScreen("0: able"), FooScreen("1: baker"), FooScreen("2: charlie"))
      )
  }

  @Test fun nullFromEmptyList() {
    assertThat(emptyList<FooScreen<*>>().toBackStackScreenOrNull()).isNull()
  }

  @Test fun throwFromEmptyList() {
    assertFailsWith<IllegalArgumentException> { emptyList<FooScreen<*>>().toBackStackScreen() }
  }

  @Test fun fromList() {
    assertThat(listOf(FooScreen(1), FooScreen(2), FooScreen(3)).toBackStackScreen())
      .isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)))
  }

  @Test fun fromListOrNull() {
    assertThat(listOf(FooScreen(1), FooScreen(2), FooScreen(3)).toBackStackScreenOrNull())
      .isEqualTo(BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)))
  }

  /**
   * To reminds us why we want the `out` in `BackStackScreen<out T : Screen>`.
   * Without this, using `BackStackScreen<*>` as `RenderingT` is not practical.
   */
  @Test fun heterogenousPlusIsTolerable() {
    val foo = BackStackScreen(FooScreen(1))
    val bar = BackStackScreen(BarScreen(1))
    val both = foo + bar
    assertThat(both).isEqualTo(foo + bar)
  }
}
