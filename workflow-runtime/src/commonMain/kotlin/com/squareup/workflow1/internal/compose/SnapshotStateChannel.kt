package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.withCurrent
import androidx.compose.runtime.snapshots.writable
import com.squareup.workflow1.internal.compose.runtime.Lock
import com.squareup.workflow1.internal.compose.runtime.withLock
import com.squareup.workflow1.internal.requireSend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.job

/**
 * Creates a [SnapshotStateChannel] that will emit into its channel until this scope is cancelled.
 */
internal fun <T> CoroutineScope.launchSnapshotStateChannel(
  capacity: Int = 16
): SnapshotStateChannel<T> {
  return SnapshotStateChannelImpl<T>(capacity).also {
    it.observeIn(this)
  }
}

/**
 * A channel that can be sent to from inside a Compose [Snapshot] but will only transmit items once
 * the snapshot is applied to the global snapshot. Items sent from the global snapshot will transmit
 * after the next snapshot is applied or [Snapshot.sendApplyNotifications] is called.
 */
internal interface SnapshotStateChannel<T> : ReceiveChannel<T> {
  fun send(value: T)
}

private class SnapshotStateChannelImpl<T>(
  private val capacity: Int,
  private val channel: Channel<T> = Channel(capacity = capacity)
) : StateObject, SnapshotStateChannel<T>, ReceiveChannel<T> by channel {

  private var record = Record()
  private val lock = Lock()

  override val firstStateRecord: StateRecord
    get() = record

  override fun prependStateRecord(value: StateRecord) {
    @Suppress("UNCHECKED_CAST")
    record = value as SnapshotStateChannelImpl<T>.Record
  }

  override fun send(value: T) {
    record.writable(this) {
      enqueue(value)
    }
  }

  fun observeIn(scope: CoroutineScope) {
    val job = scope.coroutineContext.job
    val handle = Snapshot.registerApplyObserver(applyObserver)
    job.invokeOnCompletion { handle.dispose() }
  }

  private object NONE

  private var dequeueOffset = 0

  @Suppress("UNCHECKED_CAST")
  private val applyObserver: (Set<Any>, Snapshot) -> Unit = { changed, _ ->
    if (this in changed) {
      do {
        val dequeued = record.withCurrent { record ->
          record.dequeue()
        }

        // Send to channel outside the lock.
        if (dequeued !== NONE) {
          channel.requireSend(dequeued as T)
        }
      } while (dequeued !== NONE)
    }
  }

  override fun mergeRecords(
    previous: StateRecord,
    current: StateRecord,
    applied: StateRecord
  ): StateRecord {
    val merged = applied.create() as SnapshotStateChannelImpl<*>.Record
    merged.assignFromMerging(current, applied)
    return merged
  }

  private inner class Record : StateRecord() {

    private val data = MutableVector<Any?>()
    private var offset = 0
    private var headAtAssign = 0

    override fun create(): Record = Record()

    override fun assign(value: StateRecord) {
      @Suppress("UNCHECKED_CAST")
      value as SnapshotStateChannelImpl<T>.Record

      // Only copy over unconsumed data from the other record.
      lock.withLock {
        assignLocked(value)
      }
    }

    @Suppress("UNCHECKED_CAST")
    fun assignFromMerging(
      current: StateRecord,
      applied: StateRecord
    ) {
      current as SnapshotStateChannelImpl<T>.Record
      applied as SnapshotStateChannelImpl<T>.Record

      lock.withLock {
        assignLocked(current)

        // Treat all items that were enqueued to the applied record as being enqueued after those
        // that were enqueued to the current record.
        val appliedNewItems =
          applied.data.asMutableList().subList(applied.headAtAssign, applied.data.size)
        data.addAll(appliedNewItems)
        headAtAssign = data.size
      }
    }

    private fun assignLocked(value: Record) {
      data.clear()
      val valueOffset = (dequeueOffset - value.offset).coerceIn(0, value.data.size)
      val unconsumedData = value.data.asMutableList().subList(valueOffset, value.data.size)
      data.addAll(unconsumedData)
      offset = dequeueOffset
      headAtAssign = data.size
    }

    fun enqueue(value: T) {
      lock.withLock {
        data += value
      }
    }

    fun dequeue(): Any? {
      lock.withLock {
        val localOffset = dequeueOffset - offset
        if (localOffset < data.size) {
          val dequeued = data[localOffset]
          // Null out the consumed value so it can be GC'd, but don't move things around in the
          // array yet.
          data[localOffset] = null
          dequeueOffset++
          return dequeued
        }
      }
      return NONE
    }
  }
}
