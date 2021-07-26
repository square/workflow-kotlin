package com.squareup.workflow1

import okio.Buffer
import okio.ByteString
import org.junit.Test
import kotlin.reflect.typeOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalWorkflowApi::class, ExperimentalStdlibApi::class)

class WorkflowIdentifierJvmTest {
  @Test fun `restored identifier toString`() {
    val id = TestWorkflow1.identifier
    val serializedId = id.toByteStringOrNull()!!
    val restoredId = WorkflowIdentifier.parse(serializedId)
    assertEquals(id.toString(), restoredId.toString())
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

  @Test fun `unsnapshottable identifier returns null ByteString`() {
    val id = unsnapshottableIdentifier(typeOf<TestWorkflow1>())
    assertNull(id.toByteStringOrNull())
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

  // @Test fun `read from corrupted source throws`() {
  //   val source = TestWorkflow1.identifier.toByteStringOrNull()!!
  //       .toByteArray()
  //   source.indices.reversed()
  //       .take(10)
  //       .forEach { i ->
  //         source[i] = 0
  //       }
  //   val corruptedSource = Buffer().apply { write(source) }
  //       .readByteString()
  //   assertFailsWith<ClassNotFoundException> {
  //     WorkflowIdentifier.parse(corruptedSource)
  //   }
  // }
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
}
