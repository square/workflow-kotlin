package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlin.reflect.KClass

/**
 * Implementation of [Worker] for integration tests (using [launchForTestingFromStartWith] or [launchForTestingFromStateWith]) that
 * need to simply push values into the worker from the test.
 *
 * Instances of this class are considered equivalent if they have matching [type] and [name].
 *
 * These workers can not be run concurrently – they may only be run by a single workflow at a time,
 * although they may be run multiple times sequentially. The [Flow] returned by [run] will throw an
 * exception if it is collected more than once concurrently.
 *
 * @param name String used to distinguish this worker from other [WorkerSink]s being ran by the same
 * workflow. Used to implement [doesSameWorkAs], see the kdoc on that method for more information.
 */
public class WorkerSink<T>(
  private val name: String,
  private val type: KClass<*>
) : Worker<T> {

  private val channel = Channel<T>(capacity = UNLIMITED)
  /** This could be an atomic boolean, but this way we don't need to deal with atomicfu. */
  private var active = Mutex()

  public fun send(value: T) {
    channel.trySend(value).isSuccess
  }

  override fun run(): Flow<T> = flow {
    check(active.tryLock(this@WorkerSink)) { "Expected WorkerSink not to be run concurrently." }

    // Don't use emitAll or consumeEach because we don't want to close the channel after the worker
    // is cancelled.
    try {
      for (value in channel) emit(value)
    } finally {
      active.unlock(this@WorkerSink)
    }
  }

  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
    otherWorker is WorkerSink &&
      otherWorker.name == name &&
      otherWorker.type == type

  override fun toString(): String = "${super.toString()}<$type>(name=\"$name\")"
}

@Suppress("FunctionName")
public inline fun <reified T> WorkerSink(name: String): WorkerSink<T> = WorkerSink(name, T::class)
