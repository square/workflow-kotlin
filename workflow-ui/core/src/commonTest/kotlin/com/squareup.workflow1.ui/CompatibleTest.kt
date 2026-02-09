package com.squareup.workflow1.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
class CompatibleTest {
  @Test fun different_types_do_not_match() {
    val able = object : Any() {}
    val baker = object : Any() {}

    assertFalse { compatible(able, baker) }
  }

  @Test fun same_type_matches() {
    assertTrue { compatible("Able", "Baker") }
  }

  @Test fun isCompatibleWith_is_honored() {
    data class K(override val compatibilityKey: String) : Compatible

    assertTrue { compatible(K("hey"), K("hey")) }
    assertFalse { compatible(K("hey"), K("ho")) }
  }

  @Test fun different_Compatible_types_do_not_match() {
    abstract class A : Compatible

    class Able(override val compatibilityKey: String) : A()
    class Alpha(override val compatibilityKey: String) : A()

    assertFalse { compatible(Able("Hey"), Alpha("Hey")) }
  }
}
