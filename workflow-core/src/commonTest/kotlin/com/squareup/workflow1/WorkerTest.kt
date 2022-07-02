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

  @Test fun timer_returns_equivalent_workers_keyed() {
    val worker1 = Worker.timer(1, "key")
    val worker2 = Worker.timer(1, "key")

    assertNotSame(worker1, worker2)
    assertTrue(worker1.doesSameWorkAs(worker2))
  }

  @Test fun timer_returns_non_equivalent_workers_based_on_key() {
    val worker1 = Worker.timer(1, "key1")
    val worker2 = Worker.timer(1, "key2")

    assertFalse(worker1.doesSameWorkAs(worker2))
  }

  @Test fun finished_worker_is_equivalent_to_self() {
    assertTrue(
      Worker.finished<Nothing>()
        .doesSameWorkAs(Worker.finished<Nothing>())
    )
  }

  @Test fun transformed_workers_are_equivalent_with_equivalent_source() {
    val source = Worker.create<Unit> {}
    val transformed1 = source.transform { flow -> flow.buffer(1) }
    val transformed2 = source.transform { flow -> flow.conflate() }

    assertTrue(transformed1.doesSameWorkAs(transformed2))
  }

  @Test fun transformed_workers_are_not_equivalent_with_nonequivalent_source() {
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

  @Test fun transformed_workers_transform_flows() {
    val source = flowOf(1, 2, 3).asWorker()
    val transformed = source.transform { flow -> flow.map { it.toString() } }

    val transformedValues = runBlocking {
      transformed.run()
        .toList()
    }

    assertEquals(listOf("1", "2", "3"), transformedValues)
  }
}
