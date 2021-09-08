package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class WrapperTest {
  @Test fun isCompatible() {
    assertThat(compatible(SimplestWrapper("able"), SimplestWrapper("baker"))).isTrue()
    assertThat(compatible(SimplestWrapper("able"), SimplestWrapper(42))).isFalse()
    assertThat(compatible(SimplestWrapper("able"), "able")).isFalse()
  }

  @Test fun unwrapShallow() {
    val wrapped = "String"
    assertThat(unwrap(wrapped)).isSameInstanceAs(wrapped)
  }

  @Test fun unwrapRecursive() {
    val wrapped = "String"
    val wrapping = SimplestWrapper(SimplestWrapper(SimplestWrapper(wrapped)))
    assertThat(unwrap(wrapping)).isSameInstanceAs(wrapped)
  }

  @Test fun unwrapAs() {
    val wrapped = "String"
    val wrapping = SimplestWrapper(wrapped)
    val stringMaybe: String? = unwrapOrNull(wrapping)
    val intMaybe: Int? = unwrapOrNull(wrapping)

    assertThat(stringMaybe).isSameInstanceAs(wrapped)
    assertThat(intMaybe).isNull()
  }

  private class SimplestWrapper<W : Any>(
    override val wrapped: W
  ) : Wrapper<W>
}
