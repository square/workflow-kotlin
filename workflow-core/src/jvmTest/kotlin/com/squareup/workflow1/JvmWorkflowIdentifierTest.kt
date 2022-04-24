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
