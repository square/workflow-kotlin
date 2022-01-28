package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
class ViewEnvironmentTest {
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

  private val emptyEnv = ViewEnvironment(mapOf(ViewRegistry to ViewRegistry()))

  @Test fun defaults() {
    assertThat(emptyEnv[DataHint]).isEqualTo(DataHint())
  }

  @Test fun put() {
    val environment = emptyEnv +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertThat(environment[StringHint]).isEqualTo("fnord")
    assertThat(environment[DataHint]).isEqualTo(DataHint(42, "foo"))
  }

  @Test fun `map equality`() {
    val env1 = emptyEnv +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = emptyEnv +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertThat(env1).isEqualTo(env2)
  }

  @Test fun `map inequality`() {
    val env1 = emptyEnv +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = emptyEnv +
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
    val environment = emptyEnv +
      (StringHint to "able") +
      (StringHint to "baker")

    assertThat(environment[StringHint]).isEqualTo("baker")
  }

  @Test fun `keys of the same type`() {
    val environment = emptyEnv +
      (StringHint to "able") +
      (OtherStringHint to "baker")

    assertThat(environment[StringHint]).isEqualTo("able")
    assertThat(environment[OtherStringHint]).isEqualTo("baker")
  }
}
