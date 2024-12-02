package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewEnvironmentTest {
  private object StringHint : ViewEnvironmentKey<String>() {
    override val default = ""
  }

  private object OtherStringHint : ViewEnvironmentKey<String>() {
    override val default = ""
  }

  private data class DataHint(
    val int: Int = -1,
    val string: String = ""
  ) {
    companion object : ViewEnvironmentKey<DataHint>() {
      override val default = DataHint()
    }
  }

  @Test fun defaults() {
    assertEquals(DataHint(), EMPTY[DataHint])
  }

  @Test fun put() {
    val environment = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertEquals("fnord", environment[StringHint])
    assertEquals(DataHint(42, "foo"), environment[DataHint])
  }

  @Test fun map_equality() {
    val env1 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    assertEquals(env2, env1)
  }

  @Test fun map_inequality() {
    val env1 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(42, "foo"))

    val env2 = EMPTY +
      (StringHint to "fnord") +
      (DataHint to DataHint(43, "foo"))

    assertNotEquals(env2, env1)
  }

  @Test fun key_equality() {
    assertEquals(StringHint, StringHint)
  }

  @Test fun key_inequality() {
    assertNotEquals<ViewEnvironmentKey<String>>(OtherStringHint, StringHint)
  }

  @Test fun override() {
    val environment = EMPTY +
      (StringHint to "able") +
      (StringHint to "baker")

    assertEquals("baker", environment[StringHint])
  }

  @Test fun keys_of_the_same_type() {
    val environment = EMPTY +
      (StringHint to "able") +
      (OtherStringHint to "baker")

    assertEquals("able", environment[StringHint])
    assertEquals("baker", environment[OtherStringHint])
  }

  @Test fun preserve_this_when_merging_empty() {
    val environment = EMPTY + (StringHint to "able")
    assertSame(environment, environment + EMPTY)
  }

  @Test fun preserve_other_when_merging_to_empty() {
    val environment = EMPTY + (StringHint to "able")
    assertSame(environment, EMPTY + environment)
  }

  @Test fun self_plus_self_is_self() {
    val environment = EMPTY + (StringHint to "able")
    assertSame(environment, environment + environment)
  }

  @Test fun honors_combine() {
    val combiningHint = object : ViewEnvironmentKey<String>() {
      override val default: String
        get() = error("")

      override fun combine(
        left: String,
        right: String
      ): String {
        return "$left-$right"
      }
    }

    val left = EMPTY + (combiningHint to "able")
    val right = EMPTY + (combiningHint to "baker")
    assertEquals("able-baker", (left + right)[combiningHint])
  }
}
