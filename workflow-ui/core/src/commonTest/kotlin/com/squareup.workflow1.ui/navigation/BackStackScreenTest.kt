package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackScreenTest {
  data class FooScreen<T>(val value: T) : Screen
  data class BarScreen<T>(val value: T) : Screen

  @Test fun top_is_last() {
    assertEquals(
      FooScreen(4),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)).top
    )
  }

  @Test fun backstack_is_all_but_top() {
    assertEquals(
      listOf(FooScreen(1), FooScreen(2), FooScreen(3)),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)).backStack
    )
  }

  @Test fun get_works() {
    assertEquals(
      FooScreen("baker"),
      BackStackScreen(FooScreen("able"), FooScreen("baker"), FooScreen("charlie"))[1]
    )
  }

  @Test fun plus_another_stack() {
    assertEquals(
      BackStackScreen(
        FooScreen(1),
        FooScreen(2),
        FooScreen(3),
        FooScreen(8),
        FooScreen(9),
        FooScreen(0)
      ),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)) + BackStackScreen(
        FooScreen(8),
        FooScreen(9),
        FooScreen(0)
      )
    )
  }

  @Test fun unequal_by_order() {
    assertNotEquals(
      BackStackScreen(FooScreen(3), FooScreen(2), FooScreen(1)),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3))
    )
  }

  @Test fun equal_have_matching_hash() {
    assertEquals(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode(),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode()
    )
  }

  @Test fun unequal_have_mismatching_hash() {
    assertNotEquals(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).hashCode(),
      BackStackScreen(FooScreen(1), FooScreen(2)).hashCode()
    )
  }

  @Test fun bottom_and_rest() {
    assertEquals(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3), FooScreen(4)),
      BackStackScreen.fromList(
        listOf(element = FooScreen(1)) + listOf(FooScreen(2), FooScreen(3), FooScreen(4))
      )
    )
  }

  @Test fun singleton() {
    val stack = BackStackScreen(FooScreen("hi"))
    assertEquals(FooScreen("hi"), stack.top)
    assertEquals(listOf(FooScreen("hi")), stack.frames)
    assertEquals(BackStackScreen(FooScreen("hi")), stack)
  }

  @Test fun map() {
    assertEquals(
      BackStackScreen(FooScreen(2), FooScreen(4), FooScreen(6)),
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)).map {
        FooScreen(it.value * 2)
      }
    )
  }

  @Test fun mapIndexed() {
    val source = BackStackScreen(FooScreen("able"), FooScreen("baker"), FooScreen("charlie"))
    assertEquals(
      BackStackScreen(FooScreen("0: able"), FooScreen("1: baker"), FooScreen("2: charlie")),
      source.mapIndexed { index, frame -> FooScreen("$index: ${frame.value}") }
    )
  }

  @Test fun nullFromEmptyList() {
    assertNull(emptyList<FooScreen<*>>().toBackStackScreenOrNull())
  }

  @Test fun throwFromEmptyList() {
    assertFailsWith<IllegalArgumentException> { emptyList<FooScreen<*>>().toBackStackScreen() }
  }

  @Test fun fromList() {
    assertEquals(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)),
      listOf(FooScreen(1), FooScreen(2), FooScreen(3)).toBackStackScreen()
    )
  }

  @Test fun fromListOrNull() {
    assertEquals(
      BackStackScreen(FooScreen(1), FooScreen(2), FooScreen(3)),
      listOf(FooScreen(1), FooScreen(2), FooScreen(3)).toBackStackScreenOrNull()
    )
  }

  /**
   * To reminds us why we want the `out` in `BackStackScreen<out T : Screen>`.
   * Without this, using `BackStackScreen<*>` as `RenderingT` is not practical.
   */
  @Test fun heterogenousPlusIsTolerable() {
    val foo = BackStackScreen(FooScreen(1))
    val bar = BackStackScreen(BarScreen(1))
    val both = foo + bar
    assertEquals(foo + bar, both)
  }
}
