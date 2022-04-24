package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierTest.TestUnsnapshottableImpostor
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeWorkflowIdentifierTest {

  @Test fun `unsnapshottable identifier toString`() {
    val id = unsnapshottableIdentifier(typeOf<String>())
    assertEquals(
      "WorkflowIdentifier(${String::class.qualifiedName})",
      id.toString()
    )
  }

  @Test fun `unsnapshottable impostor identifier toString`() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertEquals(
      "WorkflowIdentifier(${TestUnsnapshottableImpostor::class.qualifiedName}, " +
        "${String::class.qualifiedName})",
      id.toString()
    )
  }
}
