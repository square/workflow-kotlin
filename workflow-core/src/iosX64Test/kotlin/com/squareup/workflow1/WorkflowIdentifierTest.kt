package com.squareup.workflow1

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalWorkflowApi::class, ExperimentalStdlibApi::class)
class WorkflowIdentifierTest {

  @Test fun `flat identifier toString`() {
    val id = TestWorkflow1.identifier
    assertEquals(
        //"WorkflowIdentifier(com.squareup.workflow1.WorkflowIdentifierTest\$TestWorkflow1)",
      "WorkflowIdentifier(com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

  @Test fun `impostor identifier toString uses describeRealIdentifier when non-null`() {
    class TestImpostor : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier = TestWorkflow1.identifier
      override fun describeRealIdentifier(): String? =
        "TestImpostor(${TestWorkflow1::class.simpleName})"

      override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
        throw NotImplementedError()
    }

    val id = TestImpostor().identifier
    assertEquals("TestImpostor(TestWorkflow1)", id.toString())
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
      //"WorkflowIdentifier(${TestImpostor::class.qualifiedName}, " +
      "WorkflowIdentifier(${TestImpostor::class.simpleName}, " +
          //"com.squareup.workflow1.WorkflowIdentifierTest\$TestWorkflow1)",
          "com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

  @Test fun `impostor identifier description`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals("TestImpostor1(TestWorkflow1)", id.toString())
  }

  @Test fun `flat identifiers for same class are equal`() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow1.identifier
    assertEquals(id1, id2)
    assertEquals(id1.hashCode(), id2.hashCode())
  }

  @Test fun `flat identifiers for different classes are not equal`() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow2.identifier
    assertNotEquals(id1, id2)
  }

  @Test fun `impostor identifiers for same proxied class are equal`() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor1(TestWorkflow1).identifier
    assertEquals(impostorId1, impostorId2)
    assertEquals(impostorId1.hashCode(), impostorId2.hashCode())
  }

  @Test fun `impostor identifiers for different proxied classes are not equal`() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor1(TestWorkflow2).identifier
    assertNotEquals(impostorId1, impostorId2)
  }

  @Test fun `different impostor identifiers for same proxied class are not equal`() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor2(TestWorkflow1).identifier
    assertNotEquals(impostorId1, impostorId2)
  }


  // TODO(Fix test)
  // kotlin.AssertionError: Expected value to be null, but was:
  // <[hex=0000003b636f6d2e73717561726575702e776f726b666c6f77312e576f726b666c6f774964656e746966696572546573742e54657374576f726b666c6f773100]>.
  @Test fun `unsnapshottable identifier returns null ByteString`() {
    val id = unsnapshottableIdentifier(typeOf<TestWorkflow1>())
    assertNull(id.toByteStringOrNull())
  }

  @Test fun `unsnapshottable identifier toString()`() {
    val id = unsnapshottableIdentifier(typeOf<String>())
    assertEquals(
        //"WorkflowIdentifier(${String::class.qualifiedName} (Kotlin reflection is not available))",
      "WorkflowIdentifier(${String::class.qualifiedName})",
      id.toString()
    )
  }

  @Test fun `unsnapshottable identifiers for same class are equal`() {
    val id1 = unsnapshottableIdentifier(typeOf<String>())
    val id2 = unsnapshottableIdentifier(typeOf<String>())
    assertEquals(id1, id2)
  }

  @Test fun `unsnapshottable identifiers for different class are not equal`() {
    val id1 = unsnapshottableIdentifier(typeOf<String>())
    val id2 = unsnapshottableIdentifier(typeOf<Int>())
    assertNotEquals(id1, id2)
  }

  // TODO(Fix test)
  @Test fun `unsnapshottable impostor identifier returns null ByteString`() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertNull(id.toByteStringOrNull())
  }

  // TODO(Fix test)
  @Test fun `impostor of unsnapshottable impostor identifier returns null ByteString`() {
    val id = TestImpostor1(TestUnsnapshottableImpostor(typeOf<String>())).identifier
    assertNull(id.toByteStringOrNull())
  }

  @Test fun `unsnapshottable impostor identifier toString()`() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertEquals(
        //"WorkflowIdentifier(${TestUnsnapshottableImpostor::class.qualifiedName}, " +
          "WorkflowIdentifier(${TestUnsnapshottableImpostor::class.qualifiedName}, " +
          //"${String::class.qualifiedName} (Kotlin reflection is not available))", id.toString()
          "${String::class.qualifiedName})", id.toString()
    )
  }

/*  @Test fun `workflowIdentifier from Workflow class is equal to identifier from workflow`() {
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

  @Test fun `getRealIdentifierType() returns self for non-impostor workflow`() {
    val id = TestWorkflow1.identifier
    assertEquals(TestWorkflow1::class, id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType() returns real identifier for impostor workflow`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals(TestWorkflow1::class, id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType() returns leaf real identifier for impostor workflow chain`() {
    val id = TestImpostor2(TestImpostor1(TestWorkflow1)).identifier
    assertEquals(TestWorkflow1::class, id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType() returns KType of unsnapshottable identifier`() {
    val id = TestUnsnapshottableImpostor(typeOf<List<String>>()).identifier
    assertEquals(typeOf<List<String>>(), id.getRealIdentifierType())
  }*/

  private object TestWorkflow1 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private object TestWorkflow2 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private class TestImpostor1(
    private val proxied: Workflow<*, *, *>
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = proxied.identifier
    override fun describeRealIdentifier(): String? = "TestImpostor1(${proxied::class.simpleName})"
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private class TestImpostor2(
    proxied: Workflow<*, *, *>
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = proxied.identifier
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private class TestUnsnapshottableImpostor(
    type: KType
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = unsnapshottableIdentifier(type)
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private interface Parent : Workflow<Nothing, Nothing, Nothing>

  private object Child : Parent {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }
}
