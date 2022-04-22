package com.squareup.workflow1

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class WorkerTest {

  @Test fun `timer returns equivalent workers keyed`() {
    val worker1 = Worker.timer(1, "key")
    val worker2 = Worker.timer(1, "key")

    assertNotSame(worker1, worker2)
    assertTrue(worker1.doesSameWorkAs(worker2))
  }

  @Test fun `timer returns non-equivalent workers based on key`() {
    val worker1 = Worker.timer(1, "key1")
    val worker2 = Worker.timer(1, "key2")

    assertFalse(worker1.doesSameWorkAs(worker2))
  }

  @Test fun `finished worker is equivalent to self`() {
    assertTrue(
      Worker.finished<Nothing>()
        .doesSameWorkAs(Worker.finished<Nothing>())
    )
  }

  @Test fun `transformed workers are equivalent with equivalent source`() {
    val source = Worker.create<Unit> {}
    val transformed1 = source.transform { flow -> flow.buffer(1) }
    val transformed2 = source.transform { flow -> flow.conflate() }

    assertTrue(transformed1.doesSameWorkAs(transformed2))
  }

  @Test fun `transformed workers are not equivalent with nonequivalent source`() {
    val source1 = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = false
      override fun run(): Flow<Unit> = emptyFlow()
    }
    val source2 = object : Worker<Unit> {
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = false
      override fun run(): Flow<Unit> = emptyFlow()
    }
    val transformed1 = source1.transform { flow -> flow.conflate() }
    val transformed2 = source2.transform { flow -> flow.conflate() }

    assertFalse(transformed1.doesSameWorkAs(transformed2))
  }

  @Test fun `transformed workers transform flows`() {
    val source = flowOf(1, 2, 3).asWorker()
    val transformed = source.transform { flow -> flow.map { it.toString() } }

    val transformedValues = runBlocking {
      transformed.run()
        .toList()
    }

    assertEquals(listOf("1", "2", "3"), transformedValues)
  }
}
