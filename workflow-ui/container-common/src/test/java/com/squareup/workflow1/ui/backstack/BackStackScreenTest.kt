@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.backstack

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackScreenTest {
  @Test fun `top  is last`() {
    assertThat(BackStackScreen(1, 2, 3, 4).top).isEqualTo(4)
  }

  @Test fun `backstack is all but top`() {
    assertThat(BackStackScreen(1, 2, 3, 4).backStack).isEqualTo(listOf(1, 2, 3))
  }

  @Test fun `get works`() {
    assertThat(BackStackScreen("able", "baker", "charlie")[1]).isEqualTo("baker")
  }

  @Test fun `plus another stack`() {
    assertThat(BackStackScreen(1, 2, 3) + BackStackScreen(8, 9, 0))
        .isEqualTo(BackStackScreen(1, 2, 3, 8, 9, 0))
  }

  @Test fun `unequal by order`() {
    assertThat(BackStackScreen(1, 2, 3)).isNotEqualTo(BackStackScreen(3, 2, 1))
  }

  @Test fun `equal have matching hash`() {
    assertThat(BackStackScreen(1, 2, 3).hashCode())
        .isEqualTo(BackStackScreen(1, 2, 3).hashCode())
  }

  @Test fun `unequal have mismatching hash`() {
    assertThat(BackStackScreen(1, 2).hashCode())
        .isNotEqualTo(BackStackScreen(listOf(1, 2, 3)).hashCode())
  }

  @Test fun `bottom and rest`() {
    assertThat(
        BackStackScreen(
            bottom = 1,
            rest = listOf(2, 3, 4)
        )
    ).isEqualTo(BackStackScreen(1, 2, 3, 4))
  }

  @Test fun singleton() {
    val stack = BackStackScreen("hi")
    assertThat(stack.top).isEqualTo("hi")
    assertThat(stack.frames).isEqualTo(listOf("hi"))
    assertThat(stack).isEqualTo(BackStackScreen("hi"))
  }

  @Test fun map() {
    assertThat(BackStackScreen(1, 2, 3).map { it.toString() })
        .isEqualTo(BackStackScreen("1", "2", "3"))
  }

  @Test fun mapIndexed() {
    val source = BackStackScreen("able", "baker", "charlie")
    assertThat(source.mapIndexed { index, frame -> "$index: $frame" })
        .isEqualTo(BackStackScreen("0: able", "1: baker", "2: charlie"))
  }

  @Test fun nullFromEmptyList() {
    assertThat(emptyList<Any>().toBackStackScreenOrNull()).isNull()
  }

  @Test fun throwFromEmptyList() {
    assertFailsWith<IllegalArgumentException> { emptyList<Any>().toBackStackScreen() }
  }

  @Test fun fromList() {
    assertThat(listOf(1, 2, 3).toBackStackScreen()).isEqualTo(BackStackScreen(1, 2, 3))
  }

  @Test fun fromListOrNull() {
    assertThat(listOf(1, 2, 3).toBackStackScreenOrNull()).isEqualTo(BackStackScreen(1, 2, 3))
  }
}
