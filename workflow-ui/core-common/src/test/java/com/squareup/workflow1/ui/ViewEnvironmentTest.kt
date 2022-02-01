package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewEnvironmentTest {
  private object StringHint : ViewEnvironmentKey<String>(String::class) {
    override val default = ""
  }

  private object OtherStringHint : ViewEnvironmentKey<String>(String::class) {
    override val default = ""
  }

  private data class DataHint(
    val int: Int = -1,
    val string: String = ""
  ) {
    companion object : ViewEnvironmentKey<DataHint>(DataHint::class) {
      override val default = DataHint()
    }
  }

  @Test fun defaults() {
    assertThat(EMPTY[DataHint]).isEqualTo(DataHint())
  }

  @Test fun put() {
    val environment = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertThat(environment[StringHint]).isEqualTo("fnord")
    assertThat(environment[DataHint]).isEqualTo(DataHint(42, "foo"))
  }

  @Test fun `map equality`() {
    val env1 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertThat(env1).isEqualTo(env2)
  }

  @Test fun `map inequality`() {
    val env1 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(43, "foo"))

    assertThat(env1).isNotEqualTo(env2)
  }

  @Test fun `key equality`() {
    assertThat(StringHint).isEqualTo(StringHint)
  }

  @Test fun `key inequality`() {
    assertThat(StringHint).isNotEqualTo(OtherStringHint)
  }

  @Test fun override() {
    val environment = EMPTY +
      (StringHint to "able") +
      (StringHint to "baker")

    assertThat(environment[StringHint]).isEqualTo("baker")
  }

  @Test fun `keys of the same type`() {
    val environment = EMPTY +
      (StringHint to "able") +
      (OtherStringHint to "baker")

    assertThat(environment[StringHint]).isEqualTo("able")
    assertThat(environment[OtherStringHint]).isEqualTo("baker")
  }

  @Test fun `preserve this when merging empty`() {
    val environment = EMPTY + (StringHint to "able")
    assertThat(environment + EMPTY).isSameInstanceAs(environment)
  }

  @Test fun `preserve other when merging to empty`() {
    val environment = EMPTY + (StringHint to "able")
    assertThat(EMPTY + environment).isSameInstanceAs(environment)
  }

  @Test fun `self plus self is self`() {
    val environment = EMPTY + (StringHint to "able")
    assertThat(environment + environment).isSameInstanceAs(environment)
  }
}
