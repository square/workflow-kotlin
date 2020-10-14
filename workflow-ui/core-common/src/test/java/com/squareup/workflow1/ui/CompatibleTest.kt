package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// If you try to replace isTrue() with isTrue compilation fails.
@OptIn(WorkflowUiExperimentalApi::class)
@Suppress("UsePropertyAccessSyntax")
class CompatibleTest {
  @Test fun `Different types do not match`() {
    val able = object : Any() {}
    val baker = object : Any() {}

    assertThat(compatible(able, baker)).isFalse()
  }

  @Test fun `Same type matches`() {
    assertThat(compatible("Able", "Baker")).isTrue()
  }

  @Test fun `isCompatibleWith is honored`() {
    data class K(override val compatibilityKey: String) : Compatible

    assertThat(compatible(K("hey"), K("hey"))).isTrue()
    assertThat(compatible(K("hey"), K("ho"))).isFalse()
  }

  @Test fun `Different Compatible types do not match`() {
    abstract class A : Compatible

    class Able(override val compatibilityKey: String) : A()
    class Alpha(override val compatibilityKey: String) : A()

    assertThat(compatible(Able("Hey"), Alpha("Hey"))).isFalse()
  }
}
