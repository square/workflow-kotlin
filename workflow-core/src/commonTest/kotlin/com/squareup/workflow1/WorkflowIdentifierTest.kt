package com.squareup.workflow1

import com.squareup.workflow1.WorkflowIdentifierType.Snapshottable
import com.squareup.workflow1.WorkflowIdentifierType.Unsnapshottable
import okio.Buffer
import okio.ByteString
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
internal class WorkflowIdentifierTest {

  @Test fun `flat identifier toString`() {
    val id = TestWorkflow1.identifier
    assertEquals(
      "WorkflowIdentifier(com.squareup.workflow1.WorkflowIdentifierTest.TestWorkflow1)",
      id.toString()
    )
  }

  @Test fun `impostor identifier toString uses describeRealIdentifier when non-null`() {
    class TestImpostor : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
      override val realIdentifier: WorkflowIdentifier = TestWorkflow1.identifier
      override fun describeRealIdentifier(): String =
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

  @Test fun `restored identifier toString`() {
    val id = TestWorkflow1.identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id.toString(), restoredId.toString())
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

  @Test fun `identifier restored from source is equal to itself`() {
    val id = TestWorkflow1.identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Test fun `identifier restored from source is not equal to different identifier`() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow2.identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun `impostor identifier restored from source is equal to itself`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Test
  fun `impostor identifier restored from source is not equal to impostor with different proxied class`() { // ktlint-disable max-line-length
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor1(TestWorkflow2).identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test
  fun `impostor identifier restored from source is not equal to different impostor with same proxied class`() { // ktlint-disable max-line-length
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor2(TestWorkflow1).identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun `read from empty source throws`() {
    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(ByteString.EMPTY)
    }
  }

  @Test fun `read from invalid source throws`() {
    val source = Buffer().apply { writeUtf8("invalid data") }
      .readByteString()
    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(source)
    }
  }

  @Test fun `read from corrupted source throws`() {
    val source = TestWorkflow1.identifier.toByteStringOrNull()!!
      .toByteArray()

    val corruptedSource = Buffer().apply { write(source.dropLast(2).toByteArray()) }
      .readByteString()

    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(corruptedSource)
    }
  }

  @Test fun `unsnapshottable identifier returns null ByteString`() {
    val id = unsnapshottableIdentifier(typeOf<TestWorkflow1>())
    assertNull(id.toByteStringOrNull())
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

  @Test fun `unsnapshottable impostor identifier returns null ByteString`() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertNull(id.toByteStringOrNull())
  }

  @Test fun `impostor of unsnapshottable impostor identifier returns null ByteString`() {
    val id = TestImpostor1(TestUnsnapshottableImpostor(typeOf<String>())).identifier
    assertNull(id.toByteStringOrNull())
  }

  @Test fun `getRealIdentifierType returns self for non-impostor workflow`() {
    val id = TestWorkflow1.identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType returns real identifier for impostor workflow`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType returns leaf real identifier for impostor workflow chain`() {
    val id = TestImpostor2(TestImpostor1(TestWorkflow1)).identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.getRealIdentifierType())
  }

  @Test fun `getRealIdentifierType returns KType of unsnapshottable identifier`() {
    val id = TestUnsnapshottableImpostor(typeOf<List<String>>()).identifier
    assertEquals(Unsnapshottable(typeOf<List<String>>()), id.getRealIdentifierType())
  }

  public object TestWorkflow1 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  public object TestWorkflow2 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  public class TestImpostor1(
    private val proxied: Workflow<*, *, *>
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = proxied.identifier
    override fun describeRealIdentifier(): String = "TestImpostor1(${proxied::class.qualifiedName})"
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  public class TestImpostor2(
    proxied: Workflow<*, *, *>
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = proxied.identifier
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  public class TestUnsnapshottableImpostor(
    type: KType
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = unsnapshottableIdentifier(type)
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }
}
