/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalWorkflow::class)
class WorkflowIdentifierTest {

  @Test fun `initializer throws if both type and typeString are non-null`() {
    val error = assertFailsWith<IllegalArgumentException> {
      WorkflowIdentifier(TestWorkflow1::class, typeString = "foo", proxiedIdentifier = null)
    }
    assertTrue(error.message!!.startsWith("Either type or type string must be passed, not both"))
    assertTrue(TestWorkflow1::class.toString() in error.message!!)
    assertTrue("foo" in error.message!!)
  }

  @Test fun `flat identifier toString`() {
    val id = TestWorkflow1.identifier
    assertEquals(
        "WorkflowIdentifier(com.squareup.workflow.WorkflowIdentifierTest\$TestWorkflow1)",
        id.toString()
    )
  }

  @Test fun `impostor identifier toString`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    assertEquals(
        "WorkflowIdentifier(com.squareup.workflow.WorkflowIdentifierTest\$TestImpostor1, " +
            "com.squareup.workflow.WorkflowIdentifierTest\$TestWorkflow1)",
        id.toString()
    )
  }

  @Test fun `restored identifier toString`() {
    val id = TestWorkflow1.identifier
    val serializedId = Buffer().also(id::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
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
    val serializedId = Buffer().also(id::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Test fun `identifier restored from source is not equal to different identifier`() {
    val id1 = TestWorkflow1.identifier
    val id2 = TestWorkflow2.identifier
    val serializedId = Buffer().also(id1::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun `impostor identifier restored from source is equal to itself`() {
    val id = TestImpostor1(TestWorkflow1).identifier
    val serializedId = Buffer().also(id::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
    assertEquals(id, restoredId)
    assertEquals(id.hashCode(), restoredId.hashCode())
  }

  @Test
  fun `impostor identifier restored from source is not equal to impostor with different proxied class`() {
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor1(TestWorkflow2).identifier
    val serializedId = Buffer().also(id1::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test
  fun `impostor identifier restored from source is not equal to different impostor with same proxied class`() {
    val id1 = TestImpostor1(TestWorkflow1).identifier
    val id2 = TestImpostor2(TestWorkflow1).identifier
    val serializedId = Buffer().also(id1::write)
    val restoredId = WorkflowIdentifier.read(serializedId)
    assertNotEquals(id2, restoredId)
  }

  @Test fun `read from empty source returns null`() {
    val source = Buffer()
    assertNull(WorkflowIdentifier.read(source))
  }

  @Test fun `read from invalid source returns null`() {
    val source = Buffer().apply { writeUtf8("invalid data") }
    assertNull(WorkflowIdentifier.read(source))
  }

  @Test fun `read from corrupted source does not validate class name`() {
    val source = Buffer().also(TestWorkflow1.identifier::write)
        .readByteArray()
    source.indices.reversed()
        .take(10)
        .forEach { i ->
          source[i] = 0
        }
    val corruptedSource = Buffer().apply { write(source) }
    assertNotNull(WorkflowIdentifier.read(corruptedSource))
  }

  private object TestWorkflow1 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private object TestWorkflow2 : Workflow<Nothing, Nothing, Nothing> {
    override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
      throw NotImplementedError()
  }

  private class TestImpostor1(
    proxied: Workflow<*, *, *>
  ) : Workflow<Nothing, Nothing, Nothing>, ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier = proxied.identifier
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
