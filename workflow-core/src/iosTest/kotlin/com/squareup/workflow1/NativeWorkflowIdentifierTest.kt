package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierTest.TestImpostor1
import com.squareup.workflow1.WorkflowIdentifierTest.TestUnsnapshottableImpostor
import com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeWorkflowIdentifierTest {

  @Test fun `flat identifier toString`() {
    val id = TestWorkflow1.identifier
    assertEquals(
      "WorkflowIdentifier(com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

  @Test
  fun `impostor identifier toString uses full chain when describeRealIdentifier returns null`() {
    class TestImpostor : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier = TestWorkflow1.identifier
      override fun describeRealIdentifier(): String? = null

      override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
        throw NotImplementedError()
    }

    val id = TestImpostor().identifier
    assertEquals(
      "WorkflowIdentifier(${TestImpostor::class}, " +
        "com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

  @Test fun `impostor identifier description`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals(
      "TestImpostor1(com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

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
