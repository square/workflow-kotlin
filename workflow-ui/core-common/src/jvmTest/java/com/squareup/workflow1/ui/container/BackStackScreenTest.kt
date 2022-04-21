package com.squareup.workflow1.ui.container

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackScreenTest {
  data class S<T>(val value: T) : Screen

  @Test fun `top  is last`() {
    assertThat(BackStackScreen(S(1), S(2), S(3), S(4)).top).isEqualTo(S(4))
  }

  @Test fun `backstack is all but top`() {
    assertThat(BackStackScreen(S(1), S(2), S(3), S(4)).backStack)
      .isEqualTo(listOf(S(1), S(2), S(3)))
  }

  @Test fun `get works`() {
    assertThat(BackStackScreen(S("able"), S("baker"), S("charlie"))[1]).isEqualTo(S("baker"))
  }

  @Test fun `plus another stack`() {
    assertThat(BackStackScreen(S(1), S(2), S(3)) + BackStackScreen(S(8), S(9), S(0)))
      .isEqualTo(BackStackScreen(S(1), S(2), S(3), S(8), S(9), S(0)))
  }

  @Test fun `unequal by order`() {
    assertThat(BackStackScreen(S(1), S(2), S(3)))
      .isNotEqualTo(BackStackScreen(S(3), S(2), S(1)))
  }

  @Test fun `equal have matching hash`() {
    assertThat(BackStackScreen(S(1), S(2), S(3)).hashCode())
      .isEqualTo(BackStackScreen(S(1), S(2), S(3)).hashCode())
  }

  @Test fun `unequal have mismatching hash`() {
    assertThat(BackStackScreen(S(1), S(2)).hashCode())
      .isNotEqualTo(BackStackScreen(S(1), S(2), S(3)).hashCode())
  }

  @Test fun `bottom and rest`() {
    assertThat(
      BackStackScreen(
        bottom = S(1),
        rest = listOf(S(2), S(3), S(4))
      )
    ).isEqualTo(BackStackScreen(S(1), S(2), S(3), S(4)))
  }

  @Test fun singleton() {
    val stack = BackStackScreen(S("hi"))
    assertThat(stack.top).isEqualTo(S("hi"))
    assertThat(stack.frames).isEqualTo(listOf(S("hi")))
    assertThat(stack).isEqualTo(BackStackScreen(S("hi")))
  }

  @Test fun map() {
    assertThat(BackStackScreen(S(1), S(2), S(3)).map { S(it.value * 2) })
      .isEqualTo(BackStackScreen(S(2), S(4), S(6)))
  }

  @Test fun mapIndexed() {
    val source = BackStackScreen(S("able"), S("baker"), S("charlie"))
    assertThat(source.mapIndexed { index, frame -> S("$index: ${frame.value}") })
      .isEqualTo(BackStackScreen(S("0: able"), S("1: baker"), S("2: charlie")))
  }

  @Test fun nullFromEmptyList() {
    assertThat(emptyList<S<*>>().toBackStackScreenOrNull()).isNull()
  }

  @Test fun throwFromEmptyList() {
    assertFailsWith<IllegalArgumentException> { emptyList<S<*>>().toBackStackScreen() }
  }

  @Test fun fromList() {
    assertThat(listOf(S(1), S(2), S(3)).toBackStackScreen())
      .isEqualTo(BackStackScreen(S(1), S(2), S(3)))
  }

  @Test fun fromListOrNull() {
    assertThat(listOf(S(1), S(2), S(3)).toBackStackScreenOrNull())
      .isEqualTo(BackStackScreen(S(1), S(2), S(3)))
  }
}
