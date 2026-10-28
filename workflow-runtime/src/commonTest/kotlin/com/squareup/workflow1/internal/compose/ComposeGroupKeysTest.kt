package com.squareup.workflow1.internal.compose

import kotlin.test.Test
import kotlin.test.assertEquals

internal class ComposeGroupKeysTest {

  private val keys =
    mapOf(
      "RENDER_WORKFLOW_RESTART_GROUP" to ComposeGroupKeys.RENDER_WORKFLOW_RESTART_GROUP,
      "RENDER_WORKFLOW_SKIP_GROUP" to ComposeGroupKeys.RENDER_WORKFLOW_SKIP_GROUP,
      "RENDER_CONTEXT_CHILD_GROUP" to ComposeGroupKeys.RENDER_CONTEXT_CHILD_GROUP,
    )

  @Test
  fun each_key_is_the_hash_of_its_qualified_name() {
    keys.forEach { (name, key) ->
      val qualifiedName = "com.squareup.workflow1.internal.compose.ComposeGroupKeys.$name"
      assertEquals(qualifiedName.hashCode(), key, "Wrong value for $qualifiedName")
    }
  }

  @Test
  fun keys_are_distinct() {
    assertEquals(keys.size, keys.values.toSet().size, "Duplicate keys: $keys")
  }
}
