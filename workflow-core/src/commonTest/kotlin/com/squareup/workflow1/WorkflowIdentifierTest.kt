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

  @Test fun restored_identifier_toString() {
    val id = TestWorkflow1.identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id.toString(), restoredId.toString())
  }

  @Test fun flat_identifiers_for_same_class_are_equal() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow1.identifier
    assertEquals(id1, id2)
    assertEquals(id1.hashCode(), id2.hashCode())
  }

  @Test fun flat_identifiers_for_different_classes_are_not_equal() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow2.identifier
    assertNotEquals(id1, id2)
  }

  @Test fun impostor_identifiers_for_same_proxied_class_are_equal() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor1(TestWorkflow1).identifier
    assertEquals(impostorId1, impostorId2)
    assertEquals(impostorId1.hashCode(), impostorId2.hashCode())
  }

  @Test fun impostor_identifiers_for_different_proxied_classes_are_not_equal() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor1(TestWorkflow2).identifier
    assertNotEquals(impostorId1, impostorId2)
  }

  @Test fun different_impostor_identifiers_for_same_proxied_class_are_not_equal() {
    val impostorId1 = TestImpostor1(TestWorkflow1).identifier
    val impostorId2 = TestImpostor2(TestWorkflow1).identifier
    assertNotEquals(impostorId1, impostorId2)
  }

  @Test fun identifier_restored_from_source_is_equal_to_itself() {
    val id = TestWorkflow1.identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Test fun identifier_restored_from_source_is_not_equal_to_different_identifier() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow2.identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun impostor_identifier_restored_from_source_is_equal_to_itself() {
    val id = TestImpostor1(TestWorkflow1).identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun impostor_identifier_restored_from_source_is_not_equal_to_impostor_with_different_proxied_class() {
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor1(TestWorkflow2).identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Suppress("ktlint:standard:max-line-length")
  @Test
  fun impostor_identifier_restored_from_source_is_not_equal_to_different_impostor_with_same_proxied_class() {
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor2(TestWorkflow1).identifier
    val serializedId = id1.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun read_from_empty_source_throws() {
    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(ByteString.EMPTY)
    }
  }

  @Test fun read_from_invalid_source_throws() {
    val source = Buffer().apply { writeUtf8("invalid data") }
      .readByteString()
    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(source)
    }
  }

  @Test fun read_from_corrupted_source_throws() {
    val source = TestWorkflow1.identifier.toByteStringOrNull()!!
      .toByteArray()

    val corruptedSource = Buffer().apply { write(source.dropLast(2).toByteArray()) }
      .readByteString()

    assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier.parse(corruptedSource)
    }
  }

  @Test fun unsnapshottable_identifier_returns_null_ByteString() {
    val id = unsnapshottableIdentifier(typeOf<TestWorkflow1>())
    assertNull(id.toByteStringOrNull())
  }

  @Test fun unsnapshottable_identifiers_for_same_class_are_equal() {
    val id1 = unsnapshottableIdentifier(typeOf<String>())
    val id2 = unsnapshottableIdentifier(typeOf<String>())
    assertEquals(id1, id2)
  }

  @Test fun unsnapshottable_identifiers_for_different_class_are_not_equal() {
    val id1 = unsnapshottableIdentifier(typeOf<String>())
    val id2 = unsnapshottableIdentifier(typeOf<Int>())
    assertNotEquals(id1, id2)
  }

  @Test fun unsnapshottable_impostor_identifier_returns_null_ByteString() {
    val id = TestUnsnapshottableImpostor(typeOf<String>()).identifier
    assertNull(id.toByteStringOrNull())
  }

  @Test fun impostor_of_unsnapshottable_impostor_identifier_returns_null_ByteString() {
    val id = TestImpostor1(TestUnsnapshottableImpostor(typeOf<String>())).identifier
    assertNull(id.toByteStringOrNull())
  }

  @Test fun getRealIdentifierType_returns_self_for_non_impostor_workflow() {
    val id = TestWorkflow1.identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.realType)
  }

  @Test fun getRealIdentifierType_returns_real_identifier_for_impostor_workflow() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.realType)
  }

  @Test fun getRealIdentifierType_returns_leaf_real_identifier_for_impostor_workflow_chain() {
    val id = TestImpostor2(TestImpostor1(TestWorkflow1)).identifier
    assertEquals(Snapshottable(TestWorkflow1::class), id.realType)
  }

  @Test fun getRealIdentifierType_returns_KType_of_unsnapshottable_identifier() {
    val id = TestUnsnapshottableImpostor(typeOf<List<String>>()).identifier
    assertEquals(Unsnapshottable(typeOf<List<String>>()), id.realType)
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
    override fun describeRealIdentifier(): String =
      "TestImpostor1(${commonUniqueClassName(proxied::class)})"
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
