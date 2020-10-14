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

@OptIn(WorkflowUiExperimentalApi::class)
class NamedCompatibleTest {
  object Whut
  object Hey

  @Test fun `same type same name matches`() {
    assertThat(compatible(NamedThing(Hey, "eh"), NamedThing(Hey, "eh"))).isTrue()
  }

  @Test fun `same type diff name matches`() {
    assertThat(compatible(NamedThing(Hey, "blam"), NamedThing(Hey, "bloom"))).isFalse()
  }

  @Test fun `diff type same name no match`() {
    assertThat(compatible(NamedThing(Hey, "a"), NamedThing(Whut, "a"))).isFalse()
  }

  @Test fun recursion() {
    assertThat(
        compatible(
            NamedThing(NamedThing(Hey, "one"), "ho"),
            NamedThing(NamedThing(Hey, "one"), "ho")
        )
    ).isTrue()

    assertThat(
        compatible(
            NamedThing(NamedThing(Hey, "one"), "ho"),
            NamedThing(NamedThing(Hey, "two"), "ho")
        )
    ).isFalse()

    assertThat(
        compatible(
            NamedThing(NamedThing(Hey, "a"), "ho"),
            NamedThing(NamedThing(Whut, "a"), "ho")
        )
    ).isFalse()
  }

  @Test fun `key recursion`() {
    assertThat(NamedThing(NamedThing(Hey, "one"), "ho").compatibilityKey)
        .isEqualTo(NamedThing(NamedThing(Hey, "one"), "ho").compatibilityKey)

    assertThat(NamedThing(NamedThing(Hey, "one"), "ho").compatibilityKey)
        .isNotEqualTo(NamedThing(NamedThing(Hey, "two"), "ho").compatibilityKey)

    assertThat(NamedThing(NamedThing(Hey, "a"), "ho").compatibilityKey)
        .isNotEqualTo(NamedThing(NamedThing(Whut, "a"), "ho").compatibilityKey)
  }

  @Test fun `recursive keys are legible`() {
    assertThat(NamedThing(NamedThing(Hey, "one"), "ho").compatibilityKey)
        .isEqualTo("com.squareup.workflow1.ui.NamedCompatibleTest\$Hey-Named(one)-Named(ho)")
  }

  private class Foo(override val compatibilityKey: String) : Compatible

  @Test fun `the test Compatible class actually works`() {
    assertThat(compatible(Foo("bar"), Foo("bar"))).isTrue()
    assertThat(compatible(Foo("bar"), Foo("baz"))).isFalse()
  }

  @Test fun `wrapping custom Compatible compatibility works`() {
    assertThat(compatible(NamedThing(Foo("bar"), "name"), NamedThing(Foo("bar"), "name"))).isTrue()
    assertThat(compatible(NamedThing(Foo("bar"), "name"), NamedThing(Foo("baz"), "name"))).isFalse()
  }

  @Test fun `wrapping custom Compatible keys work`() {
    assertThat(NamedThing(Foo("bar"), "name").compatibilityKey)
        .isEqualTo(NamedThing(Foo("bar"), "name").compatibilityKey)
    assertThat(NamedThing(Foo("bar"), "name").compatibilityKey)
        .isNotEqualTo(NamedThing(Foo("baz"), "name").compatibilityKey)
  }

  class NamedThing(
    override val wrapped: Any,
    override val name: String
  ) : NamedCompatible<Any>
}
