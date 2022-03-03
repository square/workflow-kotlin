package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class LegacyNamedTest {
  object Whut
  object Hey

  @Test fun `same type same name matches`() {
    assertThat(compatible(Named(Hey, "eh"), Named(Hey, "eh"))).isTrue()
  }

  @Test fun `same type diff name matches`() {
    assertThat(compatible(Named(Hey, "blam"), Named(Hey, "bloom"))).isFalse()
  }

  @Test fun `diff type same name no match`() {
    assertThat(compatible(Named(Hey, "a"), Named(Whut, "a"))).isFalse()
  }

  @Test fun recursion() {
    assertThat(
      compatible(
        Named(Named(Hey, "one"), "ho"),
        Named(Named(Hey, "one"), "ho")
      )
    ).isTrue()

    assertThat(
      compatible(
        Named(Named(Hey, "one"), "ho"),
        Named(Named(Hey, "two"), "ho")
      )
    ).isFalse()

    assertThat(
      compatible(
        Named(Named(Hey, "a"), "ho"),
        Named(Named(Whut, "a"), "ho")
      )
    ).isFalse()
  }

  @Test fun `key recursion`() {
    assertThat(Named(Named(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo(Named(Named(Hey, "one"), "ho").compatibilityKey)

    assertThat(Named(Named(Hey, "one"), "ho").compatibilityKey)
      .isNotEqualTo(Named(Named(Hey, "two"), "ho").compatibilityKey)

    assertThat(Named(Named(Hey, "a"), "ho").compatibilityKey)
      .isNotEqualTo(Named(Named(Whut, "a"), "ho").compatibilityKey)
  }

  @Test fun `recursive keys are legible`() {
    assertThat(Named(Named(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo("com.squareup.workflow1.ui.NamedTest\$Hey+one+ho")
  }

  private class Foo(override val compatibilityKey: String) : Compatible

  @Test fun `the test Compatible class actually works`() {
    assertThat(compatible(Foo("bar"), Foo("bar"))).isTrue()
    assertThat(compatible(Foo("bar"), Foo("baz"))).isFalse()
  }

  @Test fun `wrapping custom Compatible compatibility works`() {
    assertThat(compatible(Named(Foo("bar"), "name"), Named(Foo("bar"), "name"))).isTrue()
    assertThat(compatible(Named(Foo("bar"), "name"), Named(Foo("baz"), "name"))).isFalse()
  }

  @Test fun `wrapping custom Compatible keys work`() {
    assertThat(Named(Foo("bar"), "name").compatibilityKey)
      .isEqualTo(Named(Foo("bar"), "name").compatibilityKey)
    assertThat(Named(Foo("bar"), "name").compatibilityKey)
      .isNotEqualTo(Named(Foo("baz"), "name").compatibilityKey)
  }
}
