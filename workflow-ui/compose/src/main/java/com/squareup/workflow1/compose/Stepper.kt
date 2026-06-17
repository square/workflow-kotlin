package com.squareup.workflow1.compose

import androidx.collection.ScatterMap
import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.readable
import androidx.compose.runtime.snapshots.writable

/**
 * Composes [content] and returns its return value in a list.
 *
 * Every time [content] calls [Stepper.advance] its argument is passed to [advance] and [advance] is
 * expected to update some states that are read inside [content]. The current values of all states
 * changed by [advance] are saved into a frame and pushed onto the backstack along with the last
 * value returned by [content].
 *
 * When [Stepper.goBack] is called the last frame is popped and all the states that were written by
 * [advance] are restored before recomposing [content].
 *
 * @sample com.squareup.workflow1.compose.StepperDemo
 */
@Composable
public fun <T, R> stepper(
  advance: (T) -> Unit,
  content: @Composable Stepper<T, R>.() -> R
): List<R> {
  // TODO figure out how to support rememberSaveable
  val stepperImpl = remember { StepperImpl<T, R>(advance = advance) }
  stepperImpl.advance = advance
  stepperImpl.lastRendering = content(stepperImpl)
  return stepperImpl.renderings
}

/**
 * Composes [content] and returns its return value in a list. Every time [content] calls
 * [Stepper.advance] the current values of all states changed by the `toState` block are
 * saved into a frame and pushed onto the backstack along with the last value returned by [content].
 * When [Stepper.goBack] is called the last frame is popped and all the states that were
 * written by the `toState` block are restored before recomposing [content].
 *
 * This is an overload of [stepper] that makes it easier to specify the state update function when
 * calling [Stepper.advance] instead of defining it ahead of time.
 *
 * @sample com.squareup.workflow1.compose.StepperInlineDemo
 */
// Impl note: Inline since this is just syntactic sugar, no reason to generate bytecode/API for it.
@Suppress("NOTHING_TO_INLINE")
@Composable
public inline fun <R> stepper(
  noinline content: @Composable Stepper<() -> Unit, R>.() -> R
): List<R> = stepper(advance = { it() }, content = content)

public interface Stepper<T, R> {

  /** The (possibly empty) stack of steps that came before the current one. */
  val previousSteps: List<Step<R>>

  /**
   * Pushes a new frame onto the backstack with the current state and then runs [toState].
   */
  fun advance(toState: T)

  /**
   * Pops the last frame off the backstack and restores its state.
   *
   * @return False if the stack was empty (i.e. this is a noop).
   */
  fun goBack(): Boolean
}

public interface Step<T> {
  /** The last rendering produced by this step. */
  val rendering: T

  /**
   * Runs [block] inside a snapshot such that the step state is set to its saved values from this
   * step. The snapshot is read-only, so writing to any snapshot state objects will throw.
   */
  fun <R> peekStateFromStep(block: () -> R): R
}

private class StepperImpl<T, R>(
  advance: (T) -> Unit
) : Stepper<T, R> {
  var advance: (T) -> Unit by mutableStateOf(advance)
  private val savePoints = mutableStateListOf<SavePoint>()
  var lastRendering by mutableStateOf<Any?>(NO_RENDERING)

  val renderings: List<R>
    get() = buildList(capacity = savePoints.size + 1) {
      savePoints.mapTo(this) { it.rendering }
      @Suppress("UNCHECKED_CAST")
      add(lastRendering as R)
    }

  override val previousSteps: List<Step<R>>
    get() = savePoints

  override fun advance(toState: T) {
    check(lastRendering !== NO_RENDERING) { "advance called before first composition" }

    // Take an outer snapshot so all the state mutations in withState get applied atomically with
    // our internal state update (to savePoints).
    Snapshot.withMutableSnapshot {
      val savedRecords = mutableScatterMapOf<StateObject, StateRecord?>()
      val snapshot = Snapshot.takeMutableSnapshot(
        writeObserver = {
          // Don't save the value of the object yet, we want the value _before_ the write, so we
          // need to read it outside this inner snapshot.
          savedRecords[it as StateObject] = null
        }
      )
      try {
        // Record what state objects are written by the block.
        snapshot.enter { this.advance.invoke(toState) }

        // Save the _current_ values of those state objects so we can restore them later.
        // TODO Need to think more about which state objects need to be saved and restored for a
        //  particular frame. E.g. probably we should track all objects that were written for the
        //  current frame, and save those as well, even if they're not written by the _next_ frame.
        savedRecords.forEachKey { stateObject ->
          savedRecords[stateObject] = stateObject.copyCurrentRecord()
        }

        // This should never fail since we're already in a snapshot and no other state has been
        // written by this point, but check just in case.
        val advanceApplyResult = snapshot.apply()
        if (advanceApplyResult.succeeded) {
          // This cast is fine, we know we've assigned a non-null value to all entries.
          @Suppress("UNCHECKED_CAST")
          savePoints += SavePoint(
            savedRecords = savedRecords as ScatterMap<StateObject, StateRecord>,
            rendering = lastRendering as R,
          )
        }
        // If !succeeded, throw the standard error.
        advanceApplyResult.check()
      } finally {
        snapshot.dispose()
      }
    }
  }

  override fun goBack(): Boolean {
    Snapshot.withMutableSnapshot {
      if (savePoints.isEmpty()) return false
      val toRestore = savePoints.removeAt(savePoints.lastIndex)

      // Restore all state objects' saved values.
      toRestore.restoreState()

      // Don't need to restore the last rendering, it will be computed fresh by the imminent
      // recomposition.
    }
    return true
  }

  /**
   * Returns a copy of the current readable record of this state object. A copy is needed since
   * active records can be mutated by other snapshots.
   */
  private fun StateObject.copyCurrentRecord(): StateRecord {
    val record = firstStateRecord.readable(this)
    // Records can be mutated in other snapshots, so create a copy.
    return record.create().apply { assign(record) }
  }

  /**
   * Sets the value of this state object to a [record] that was previously copied via
   * [copyCurrentRecord].
   */
  private fun StateObject.restoreRecord(record: StateRecord) {
    firstStateRecord.writable(this) { assign(record) }
  }

  private inner class SavePoint(
    val savedRecords: ScatterMap<StateObject, StateRecord>,
    override val rendering: R,
  ) : Step<R> {
    override fun <R> peekStateFromStep(block: () -> R): R {
      // Need a mutable snapshot to restore state.
      val restoreSnapshot = Snapshot.takeMutableSnapshot()
      try {
        restoreSnapshot.enter {
          restoreState()

          // Now take a read-only snapshot to enforce contract.
          val readOnlySnapshot = Snapshot.takeSnapshot()
          try {
            return readOnlySnapshot.enter(block)
          } finally {
            readOnlySnapshot.dispose()
          }
        }
      } finally {
        restoreSnapshot.dispose()
      }
    }

    fun restoreState() {
      savedRecords.forEach { stateObject, record ->
        stateObject.restoreRecord(record)
      }
    }
  }

  companion object {
    val NO_RENDERING = Any()
  }
}
