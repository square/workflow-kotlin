package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class NamedTest {
  object Whut : Screen
  object Hey : Screen

  @Test fun `same type same name matches`() {
    assertThat(compatible(NamedRendering(Hey, "eh"), NamedRendering(Hey, "eh"))).isTrue()
  }

  @Test fun `same type diff name matches`() {
    assertThat(compatible(NamedRendering(Hey, "blam"), NamedRendering(Hey, "bloom"))).isFalse()
  }

  @Test fun `diff type same name no match`() {
    assertThat(compatible(NamedRendering(Hey, "a"), NamedRendering(Whut, "a"))).isFalse()
  }

  @Test fun recursion() {
    assertThat(
      compatible(
        NamedRendering(NamedRendering(Hey, "one"), "ho"),
        NamedRendering(NamedRendering(Hey, "one"), "ho")
      )
    ).isTrue()

    assertThat(
      compatible(
        NamedRendering(NamedRendering(Hey, "one"), "ho"),
        NamedRendering(NamedRendering(Hey, "two"), "ho")
      )
    ).isFalse()

    assertThat(
      compatible(
        NamedRendering(NamedRendering(Hey, "a"), "ho"),
        NamedRendering(NamedRendering(Whut, "a"), "ho")
      )
    ).isFalse()
  }

  @Test fun `key recursion`() {
    assertThat(NamedRendering(NamedRendering(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo(NamedRendering(NamedRendering(Hey, "one"), "ho").compatibilityKey)

    assertThat(NamedRendering(NamedRendering(Hey, "one"), "ho").compatibilityKey)
      .isNotEqualTo(NamedRendering(NamedRendering(Hey, "two"), "ho").compatibilityKey)

    assertThat(NamedRendering(NamedRendering(Hey, "a"), "ho").compatibilityKey)
      .isNotEqualTo(NamedRendering(NamedRendering(Whut, "a"), "ho").compatibilityKey)
  }

  @Test fun `recursive keys are legible`() {
    assertThat(NamedRendering(NamedRendering(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo("com.squareup.workflow1.ui.NamedTest\$Hey+one+ho")
  }

  private class Foo(override val compatibilityKey: String) : Screen, Compatible

  @Test fun `the test Compatible class actually works`() {
    assertThat(compatible(Foo("bar"), Foo("bar"))).isTrue()
    assertThat(compatible(Foo("bar"), Foo("baz"))).isFalse()
  }

  @Test fun `wrapping custom Compatible compatibility works`() {
    assertThat(compatible(NamedRendering(Foo("bar"), "name"), NamedRendering(Foo("bar"), "name"))).isTrue()
    assertThat(compatible(NamedRendering(Foo("bar"), "name"), NamedRendering(Foo("baz"), "name"))).isFalse()
  }

  @Test fun `wrapping custom Compatible keys work`() {
    assertThat(NamedRendering(Foo("bar"), "name").compatibilityKey)
      .isEqualTo(NamedRendering(Foo("bar"), "name").compatibilityKey)
    assertThat(NamedRendering(Foo("bar"), "name").compatibilityKey)
      .isNotEqualTo(NamedRendering(Foo("baz"), "name").compatibilityKey)
  }
}
