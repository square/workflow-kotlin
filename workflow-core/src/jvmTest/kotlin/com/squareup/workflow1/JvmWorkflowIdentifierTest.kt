package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierTest.TestImpostor1
import com.squareup.workflow1.WorkflowIdentifierTest.TestUnsnapshottableImpostor
import com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1
import com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow2
import org.junit.Test
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class JvmWorkflowIdentifierTest {

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

  @Test fun `workflowIdentifier from Workflow class is equal to identifier from workflow`() {
    val instanceId = TestWorkflow1.identifier
    val classId = TestWorkflow1::class.workflowIdentifier
    assertEquals(instanceId, classId)
  }

  @Test
  fun `workflowIdentifier from Workflow class is not equal to identifier from different class`() {
    val id1 = TestWorkflow1::class.workflowIdentifier
    val id2 = TestWorkflow2::class.workflowIdentifier
    assertNotEquals(id1, id2)
  }

  @Test fun `workflowIdentifier from ImpostorWorkflow class throws`() {
    val error = assertFailsWith<IllegalArgumentException> {
      TestImpostor1::class.workflowIdentifier
    }
    assertEquals(
      "Cannot create WorkflowIdentifier from a KClass of ImpostorWorkflow: " +
        TestImpostor1::class.qualifiedName,
      error.message
    )
  }

  @Test fun `unsnapshottable identifier toString()`() {
    val id = unsnapshottableIdentifier(typeOf<String>())
    assertEquals(
      "WorkflowIdentifier(${String::class.java.name} (Kotlin reflection is not available))",
      id.toString()
    )
  }

  @Test fun `unsnapshottable impostor identifier toString()`() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertEquals(
      "WorkflowIdentifier(${TestUnsnapshottableImpostor::class.qualifiedName}, " +
        "${String::class.java.name} (Kotlin reflection is not available))",
      id.toString()
    )
  }
}
