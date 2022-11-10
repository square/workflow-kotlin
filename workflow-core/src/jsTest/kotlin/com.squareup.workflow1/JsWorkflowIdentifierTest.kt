package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierTest.TestImpostor1
import com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1
import com.squareup.workflow1.mocks.workflows1.JsMockWorkflow1
import com.squareup.workflow1.mocks.workflows1.JsMockWorkflow2
import kotlin.js.RegExp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
internal class JsWorkflowIdentifierTest {
  @Test fun flat_identifier_toString() {
    val id = JsMockWorkflow1().identifier

    // Due to the dynamic naming of workflow identifiers (based on other identifiers that have been
    // created so far), we can only verify the composition of the name.
    // Expected value should be something like this: "WorkflowIdentifier(JsMockWorkflow1(7))"
    val idStructure = RegExp("WorkflowIdentifier\\(JsMockWorkflow1\\((\\d)+\\)\\)")
    assertTrue(idStructure.test(id.toString()))
  }

  @Test
  fun impostor_identifier_toString_uses_full_chain_when_describeRealIdentifier_returns_null() {
    class TestImpostor : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier = TestWorkflow1.identifier
      override fun describeRealIdentifier(): String? = null

      override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
        throw NotImplementedError()
    }

    val id = TestImpostor().identifier

    // Expected value should be something like this:
    // "WorkflowIdentifier(TestImpostor(3), TestWorkflow1(1))"
    val idStructure = RegExp(
      "WorkflowIdentifier\\(TestImpostor\\((\\d)+\\), TestWorkflow1\\((\\d)+\\)\\)"
    )
    assertTrue(idStructure.test(id.toString()))
  }

  @Test fun impostor_identifier_description() {
    // Expected value should be something like this: "WorkflowIdentifier(TestWorkflow1(1))"
    val id = TestImpostor1(TestWorkflow1).identifier
    val idStructure = RegExp("TestImpostor1\\(TestWorkflow1\\((\\d)+\\)\\)")
    assertTrue(idStructure.test(id.toString()))
  }

  @Test fun same_workflow_returns_same_identifier() {
    val id1 = JsMockWorkflow1().identifier
    val id2 = JsMockWorkflow1().identifier
    assertEquals(id1.toString(), id2.toString())
  }

  @Test fun different_workflows_same_namespace() {
    val id1 = JsMockWorkflow1().identifier
    val id2 = JsMockWorkflow2().identifier
    assertNotEquals(id1.toString(), id2.toString())
  }

  @Test fun same_workflow_name_different_namespace() {
    val id1 = JsMockWorkflow1().identifier
    val id2 = com.squareup.workflow1.mocks.workflows2.JsMockWorkflow1().identifier
    assertNotEquals(id1.toString(), id2.toString())
  }
}
