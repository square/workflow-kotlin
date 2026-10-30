package com.squareup.workflow1.internal.compose

import androidx.collection.MutableIntSet

/**
 * Tracks the keys passed to one kind of `BaseRenderContext` call during a single render pass, so
 * duplicates can be rejected the same way the traditional runtime rejects them.
 *
 * Compose itself happily accepts multiple movable groups with the same key, so without this check
 * duplicate calls would silently share or swap state.
 *
 * Each key is a tuple of up to three parts: [first], [second], and [inputs]. [inputs] is compared
 * by content. Callers pass a cheap [hash] that only needs to be consistent with equality; parts
 * that are expensive to hash may be left out of it. Hashes are kept in an open-addressing int set
 * and the tuples in a flat list, both reused across render passes, so after the first few passes
 * [add] does not allocate. The full tuples are only compared when hashes collide.
 */
internal class RenderPassKeys {
  private var hashes: MutableIntSet? = null

  /** Flattened tuples of 3 elements each. */
  private var parts: ArrayList<Any?>? = null

  /** Forgets all keys seen so far. Must be called at the start of every render pass. */
  fun reset() {
    // Avoid touching anything if nothing was added, which is the common case for most call types.
    if (parts?.isEmpty() == false) {
      hashes!!.clear()
      parts!!.clear()
    }
  }

  /** Records a key and returns true if it has not been seen since the last [reset]. */
  fun add(hash: Int, first: Any?, second: Any?, inputs: Array<out Any?>? = null): Boolean {
    val hashes = hashes ?: MutableIntSet().also { hashes = it }
    val parts = parts ?: ArrayList<Any?>().also { parts = it }

    if (!hashes.add(hash)) {
      // Hash collision, check for a real duplicate.
      for (i in 0 until parts.size step 3) {
        if (
          parts[i] == first &&
            parts[i + 1] == second &&
            (parts[i + 2] as Array<*>?).contentEquals(inputs)
        ) {
          return false
        }
      }
    }

    parts.add(first)
    parts.add(second)
    parts.add(inputs)
    return true
  }
}
