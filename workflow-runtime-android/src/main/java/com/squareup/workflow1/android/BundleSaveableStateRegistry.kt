package com.squareup.workflow1.android

import android.os.Binder
import android.os.Bundle
import android.os.Parcelable
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.snapshots.SnapshotMutableState
import androidx.compose.runtime.structuralEqualityPolicy
import com.squareup.workflow1.Snapshot
import java.io.Serializable

/**
 * A [SaveableStateRegistry] that can save and restore anything that can be saved in a [Bundle].
 *
 * Similar to Compose Android's `DisposableSaveableStateRegistry`.
 */
internal class BundleSaveableStateRegistry private constructor(
  saveableStateRegistry: SaveableStateRegistry
) : SaveableStateRegistry by saveableStateRegistry {
  constructor(restoredValues: Map<String, List<Any?>>?) : this(
    SaveableStateRegistry(restoredValues, ::canBeSavedToBundle)
  )

  // TODO move the functions from SnapshotParcels.kt into runtime-android.
  // constructor(snapshot: Snapshot) : this(snapshot.toParcelable<Bundle>().toMap())
  //
  // fun toSnapshot(): Snapshot = performSave().toBundle().toSnapshot()
}

/**
 * Checks that [value] can be stored inside [Bundle].
 */
private fun canBeSavedToBundle(value: Any): Boolean {
  // SnapshotMutableStateImpl is Parcelable, but we do extra checks
  if (value is SnapshotMutableState<*>) {
    if (value.policy === neverEqualPolicy<Any?>() ||
      value.policy === structuralEqualityPolicy<Any?>() ||
      value.policy === referentialEqualityPolicy<Any?>()
    ) {
      val stateValue = value.value
      return if (stateValue == null) true else canBeSavedToBundle(stateValue)
    } else {
      return false
    }
  }
  // lambdas in Kotlin implement Serializable, but will crash if you really try to save them.
  // we check for both Function and Serializable (see kotlin.jvm.internal.Lambda) to support
  // custom user defined classes implementing Function interface.
  if (value is Function<*> && value is Serializable) {
    return false
  }
  for (cl in AcceptableClasses) {
    if (cl.isInstance(value)) {
      return true
    }
  }
  return false
}

/**
 * Contains Classes which can be stored inside [Bundle].
 *
 * Some of the classes are not added separately because:
 *
 * - These classes implement Serializable:
 *     - Arrays (DoubleArray, BooleanArray, IntArray, LongArray, ByteArray, FloatArray, ShortArray,
 *       CharArray, Array<Parcelable>, Array<String>)
 *     - ArrayList
 *     - Primitives (Boolean, Int, Long, Double, Float, Byte, Short, Char) will be boxed when casted
 *       to Any, and all the boxed classes implements Serializable.
 * - This class implements Parcelable:
 *     - Bundle
 *
 * Note: it is simplified copy of the array from SavedStateHandle (lifecycle-viewmodel-savedstate).
 */
private val AcceptableClasses = arrayOf(
  Serializable::class.java,
  Parcelable::class.java,
  String::class.java,
  SparseArray::class.java,
  Binder::class.java,
  Size::class.java,
  SizeF::class.java
)

@Suppress("DEPRECATION")
private fun Bundle.toMap(): Map<String, List<Any?>>? {
  val map = mutableMapOf<String, List<Any?>>()
  this.keySet().forEach { key ->
    @Suppress("UNCHECKED_CAST")
    val list = getParcelableArrayList<Parcelable?>(key) as ArrayList<Any?>
    map[key] = list
  }
  return map
}

private fun Map<String, List<Any?>>.toBundle(): Bundle {
  val bundle = Bundle()
  forEach { (key, list) ->
    val arrayList = if (list is ArrayList<Any?>) list else ArrayList(list)
    @Suppress("UNCHECKED_CAST")
    bundle.putParcelableArrayList(key, arrayList as ArrayList<Parcelable?>)
  }
  return bundle
}
