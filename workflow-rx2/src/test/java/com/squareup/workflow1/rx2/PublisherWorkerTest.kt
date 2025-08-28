@file:Suppress("DEPRECATION")

package com.squareup.workflow1.rx2

import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateless
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import io.reactivex.BackpressureStrategy.BUFFER
import io.reactivex.subjects.PublishSubject
import org.reactivestreams.Publisher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class PublisherWorkerTest {

  @Test fun works() {
    val subject = PublishSubject.create<String>()
    val worker = object : PublisherWorker<String>() {
      override fun runPublisher(): Publisher<out String> = subject.toFlowable(BUFFER)
      override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean = otherWorker === this
    }

    fun action(value: String) = action<Unit, Nothing, String>("") { setOutput(value) }
    val workflow = Workflow.stateless<Unit, String, Unit> {
      runningWorker(worker) { action(it) }
    }

    workflow.launchForTestingFromStartWith {
      assertFalse(hasOutput)

      subject.onNext("one")
      assertEquals("one", awaitNextOutput())

      subject.onNext("two")
      subject.onNext("three")
      assertEquals("two", awaitNextOutput())
      assertEquals("three", awaitNextOutput())
    }
  }
}
