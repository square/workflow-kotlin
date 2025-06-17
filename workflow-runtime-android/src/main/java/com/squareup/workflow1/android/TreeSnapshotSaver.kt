package com.squareup.workflow1.android

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.savedstate.SavedStateRegistry
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.android.TreeSnapshotSaver.Companion.fromSavedStateRegistry

/**
 * Persistence aid for [TreeSnapshot]. Use [fromSavedStateRegistry] to create one
 * that works with androidx [SavedStateRegistry].
 */
internal interface TreeSnapshotSaver {
  /**
   * Implemented by the object that is running a workflow session, to give
   * on demand access to the most recent [TreeSnapshot] as required for persistence.
   */
  interface HasTreeSnapshot {
    fun latestSnapshot(): TreeSnapshot?
  }

  /** Consumes and returns the saved [TreeSnapshot], or null if there wasn't one. */
  fun consumeSnapshot(): TreeSnapshot?

  fun registerSource(source: HasTreeSnapshot)

  companion object {
    fun fromSavedStateRegistry(savedStateRegistry: SavedStateRegistry) =
      object : TreeSnapshotSaver {
        override fun consumeSnapshot(): TreeSnapshot? {
          return if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            savedStateRegistry
              .consumeRestoredStateForKey(BUNDLE_KEY)
              ?.getParcelable<PickledTreesnapshot>(BUNDLE_KEY, PickledTreesnapshot::class.java)
              ?.snapshot
          } else {
            @Suppress("DEPRECATION")
            savedStateRegistry
              .consumeRestoredStateForKey(BUNDLE_KEY)
              ?.getParcelable<PickledTreesnapshot>(BUNDLE_KEY)
              ?.snapshot
          }
        }

        override fun registerSource(source: HasTreeSnapshot) {
          savedStateRegistry.registerSavedStateProvider(BUNDLE_KEY) {
            Bundle().apply {
              source.latestSnapshot()?.let {
                putParcelable(BUNDLE_KEY, PickledTreesnapshot(it))
              }
            }
          }
        }
      }

    /**
     * Namespace key, used in two places:
     *  - names our slot in the [SavedStateRegistry]
     *  - and is also the key for the [PickledTreesnapshot] we write to the bundle
     */
    val BUNDLE_KEY = TreeSnapshotSaver::class.java.name
  }
}
