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

// If you try to replace isTrue() with isTrue compilation fails.
@OptIn(WorkflowUiExperimentalApi::class)
@Suppress("UsePropertyAccessSyntax")
class NamedTest {
  object Whut
  object Hey

  @Test fun `same type same name matches`() {
    assertThat(compatible(NamedCompatible(Hey, "eh"), NamedCompatible(Hey, "eh"))).isTrue()
  }

  @Test fun `same type diff name matches`() {
    assertThat(compatible(NamedCompatible(Hey, "blam"), NamedCompatible(Hey, "bloom"))).isFalse()
  }

  @Test fun `diff type same name no match`() {
    assertThat(compatible(NamedCompatible(Hey, "a"), NamedCompatible(Whut, "a"))).isFalse()
  }

  @Test fun recursion() {
    assertThat(
        compatible(
            NamedCompatible(NamedCompatible(Hey, "one"), "ho"),
            NamedCompatible(NamedCompatible(Hey, "one"), "ho")
        )
    ).isTrue()

    assertThat(
        compatible(
            NamedCompatible(NamedCompatible(Hey, "one"), "ho"),
            NamedCompatible(NamedCompatible(Hey, "two"), "ho")
        )
    ).isFalse()

    assertThat(
        compatible(
            NamedCompatible(NamedCompatible(Hey, "a"), "ho"),
            NamedCompatible(NamedCompatible(Whut, "a"), "ho")
        )
    ).isFalse()
  }

  @Test fun `key recursion`() {
    assertThat(NamedCompatible(NamedCompatible(Hey, "one"), "ho").compatibilityKey)
        .isEqualTo(NamedCompatible(NamedCompatible(Hey, "one"), "ho").compatibilityKey)

    assertThat(NamedCompatible(NamedCompatible(Hey, "one"), "ho").compatibilityKey)
        .isNotEqualTo(NamedCompatible(NamedCompatible(Hey, "two"), "ho").compatibilityKey)

    assertThat(NamedCompatible(NamedCompatible(Hey, "a"), "ho").compatibilityKey)
        .isNotEqualTo(NamedCompatible(NamedCompatible(Whut, "a"), "ho").compatibilityKey)
  }

  @Test fun `recursive keys are legible`() {
    assertThat(NamedCompatible(NamedCompatible(Hey, "one"), "ho").compatibilityKey)
        .isEqualTo("com.squareup.workflow1.ui.NamedTest\$Hey-Named(one)-Named(ho)")
  }

  private class Foo(override val compatibilityKey: String) : Compatible

  @Test fun `the test Compatible class actually works`() {
    assertThat(compatible(Foo("bar"), Foo("bar"))).isTrue()
    assertThat(compatible(Foo("bar"), Foo("baz"))).isFalse()
  }

  @Test fun `wrapping custom Compatible compatibility works`() {
    assertThat(compatible(NamedCompatible(Foo("bar"), "name"), NamedCompatible(Foo("bar"), "name"))).isTrue()
    assertThat(compatible(NamedCompatible(Foo("bar"), "name"), NamedCompatible(Foo("baz"), "name"))).isFalse()
  }

  @Test fun `wrapping custom Compatible keys work`() {
    assertThat(NamedCompatible(Foo("bar"), "name").compatibilityKey)
        .isEqualTo(NamedCompatible(Foo("bar"), "name").compatibilityKey)
    assertThat(NamedCompatible(Foo("bar"), "name").compatibilityKey)
        .isNotEqualTo(NamedCompatible(Foo("baz"), "name").compatibilityKey)
  }
}
