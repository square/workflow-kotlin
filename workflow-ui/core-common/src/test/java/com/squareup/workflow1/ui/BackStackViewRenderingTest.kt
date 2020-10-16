/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
class BackStackViewRenderingTest {
  @Test fun `top  is last`() {
    assertThat(BackStackViewRendering(One, Two, Three, Four).top).isEqualTo(Four)
  }

  @Test fun `backstack is all but top`() {
    assertThat(BackStackViewRendering(One, Two, Three, Four).backStack).isEqualTo(
        listOf(One, Two, Three)
    )
  }

  @Test fun `get works`() {
    assertThat(BackStackViewRendering(Able(), Baker(), Charlie())[1]).isEqualTo(Baker())
  }

  @Test fun `plus another stack`() {
    assertThat(
        BackStackViewRendering(One, Two, Three) + BackStackViewRendering(Able(), Baker(), Charlie())
    )
        .isEqualTo(BackStackViewRendering(One, Two, Three, Able(), Baker(), Charlie()))
  }

  @Test fun `unequal by order`() {
    assertThat(BackStackViewRendering(One, Two, Three)).isNotEqualTo(
        BackStackViewRendering(Three, Two, One)
    )
  }

  @Test fun `equal have matching hash`() {
    assertThat(BackStackViewRendering(One, Two, Three).hashCode())
        .isEqualTo(BackStackViewRendering(One, Two, Three).hashCode())
  }

  @Test fun `unequal have mismatching hash`() {
    assertThat(BackStackViewRendering(One, Two).hashCode())
        .isNotEqualTo(BackStackViewRendering(One, Two, Three).hashCode())
  }

  @Test fun `bottom and rest`() {
    assertThat(
        BackStackViewRendering(
            bottom = One,
            rest = listOf(Two, Three, Four)
        )
    ).isEqualTo(BackStackViewRendering(One, Two, Three, Four))
  }

  @Test fun singleton() {
    val stack = BackStackViewRendering(Able())
    assertThat(stack.top).isEqualTo(Able())
    assertThat(stack.frames).isEqualTo(listOf(Able()))
    assertThat(stack).isEqualTo(BackStackViewRendering(Able()))
  }

  @Test fun map() {
    assertThat(BackStackViewRendering(One, Two, Three).map {
      when (it) {
        One -> Able()
        Two -> Baker()
        Three -> Charlie()
        else -> error("oops")
      }
    }).isEqualTo(BackStackViewRendering(Able(), Baker(), Charlie()))
  }

  @Test fun mapIndexed() {
    val source = BackStackViewRendering(Able(), Baker(), Charlie())
    assertThat(source.mapIndexed { index, frame ->
      when (frame) {
        One -> Able(index)
        Two -> Baker(index)
        Three -> Charlie(index)
        else -> error("oops")
      }
    }).isEqualTo(BackStackViewRendering(Able(0), Baker(1), Charlie(2)))
  }

  @Test fun nullFromEmptyList() {
    assertThat(emptyList<ViewRendering>().toBackStackScreenOrNull()).isNull()
  }

  @Test fun throwFromEmptyList() {
    assertFailsWith<IllegalArgumentException> { emptyList<ViewRendering>().toBackStackScreen() }
  }

  @Test fun fromList() {
    assertThat(listOf(One, Two, Three).toBackStackScreen()).isEqualTo(
        BackStackViewRendering(One, Two, Three)
    )
  }

  @Test fun fromListOrNull() {
    assertThat(listOf(One, Two, Three).toBackStackScreenOrNull()).isEqualTo(
        BackStackViewRendering(One, Two, Three)
    )
  }

  private object One : ViewRendering
  private object Two : ViewRendering
  private object Three : ViewRendering
  private object Four : ViewRendering

  private data class Able(val i: Int = -1) : ViewRendering
  private data class Baker(val i: Int = -1) : ViewRendering
  private data class Charlie(val i: Int = -1) : ViewRendering
}
