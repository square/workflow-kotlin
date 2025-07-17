package com.squareup.workflow1.ui

import android.os.Parcelable
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.toParcelable
import com.squareup.workflow1.toSnapshot
import okio.ByteString

/**
 * Wraps receiver in a [Snapshot] suitable for use with [com.squareup.workflow1.StatefulWorkflow].
 * Intended to allow use of `@Parcelize`.
 *
 * Read the [Parcelable] back with [toParcelable].
 */
@Deprecated(
  "Use toSnapshot() from workflow-core instead.",
  replaceWith = ReplaceWith("toSnapshot()", "com.squareup.workflow1.toSnapshot")
)
public fun Parcelable.toSnapshot(): Snapshot = toSnapshot()

/**
 * @return a [Parcelable] previously wrapped with [toSnapshot], or `null` if the receiver is empty.
 */
@Deprecated(
  "Use toParcelable() from workflow-core instead.",
  replaceWith = ReplaceWith("toParcelable()", "com.squareup.workflow1.toParcelable")
)
public inline fun <reified T : Parcelable> Snapshot.toParcelable(): T? = toParcelable()

@Deprecated(
  "Use toParcelable() from workflow-core instead.",
  replaceWith = ReplaceWith("toParcelable()", "com.squareup.workflow1.toParcelable")
)
public inline fun <reified T : Parcelable> ByteString.toParcelable(): T = toParcelable<T>()!!
