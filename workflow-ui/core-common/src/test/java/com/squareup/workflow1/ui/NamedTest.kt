package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class NamedTest {
  object Whut : Screen
  object Hey : Screen

  @Test fun `same type same name matches`() {
    assertThat(compatible(NamedScreen(Hey, "eh"), NamedScreen(Hey, "eh"))).isTrue()
  }

  @Test fun `same type diff name matches`() {
    assertThat(compatible(NamedScreen(Hey, "blam"), NamedScreen(Hey, "bloom"))).isFalse()
  }

  @Test fun `diff type same name no match`() {
    assertThat(compatible(NamedScreen(Hey, "a"), NamedScreen(Whut, "a"))).isFalse()
  }

  @Test fun recursion() {
    assertThat(
      compatible(
        NamedScreen(NamedScreen(Hey, "one"), "ho"),
        NamedScreen(NamedScreen(Hey, "one"), "ho")
      )
    ).isTrue()

    assertThat(
      compatible(
        NamedScreen(NamedScreen(Hey, "one"), "ho"),
        NamedScreen(NamedScreen(Hey, "two"), "ho")
      )
    ).isFalse()

    assertThat(
      compatible(
        NamedScreen(NamedScreen(Hey, "a"), "ho"),
        NamedScreen(NamedScreen(Whut, "a"), "ho")
      )
    ).isFalse()
  }

  @Test fun `key recursion`() {
    assertThat(NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo(NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey)

    assertThat(NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey)
      .isNotEqualTo(NamedScreen(NamedScreen(Hey, "two"), "ho").compatibilityKey)

    assertThat(NamedScreen(NamedScreen(Hey, "a"), "ho").compatibilityKey)
      .isNotEqualTo(NamedScreen(NamedScreen(Whut, "a"), "ho").compatibilityKey)
  }

  @Test fun `recursive keys are legible`() {
    assertThat(NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey)
      .isEqualTo("com.squareup.workflow1.ui.NamedTest\$Hey+one+ho")
  }

  private class Foo(override val compatibilityKey: String) : Screen, Compatible

  @Test fun `the test Compatible class actually works`() {
    assertThat(compatible(Foo("bar"), Foo("bar"))).isTrue()
    assertThat(compatible(Foo("bar"), Foo("baz"))).isFalse()
  }

  @Test fun `wrapping custom Compatible compatibility works`() {
    assertThat(compatible(NamedScreen(Foo("bar"), "name"), NamedScreen(Foo("bar"), "name"))).isTrue()
    assertThat(compatible(NamedScreen(Foo("bar"), "name"), NamedScreen(Foo("baz"), "name"))).isFalse()
  }

  @Test fun `wrapping custom Compatible keys work`() {
    assertThat(NamedScreen(Foo("bar"), "name").compatibilityKey)
      .isEqualTo(NamedScreen(Foo("bar"), "name").compatibilityKey)
    assertThat(NamedScreen(Foo("bar"), "name").compatibilityKey)
      .isNotEqualTo(NamedScreen(Foo("baz"), "name").compatibilityKey)
  }
}
