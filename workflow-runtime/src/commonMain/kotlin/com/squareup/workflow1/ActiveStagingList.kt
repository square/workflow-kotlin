package com.squareup.workflow1

import com.squareup.workflow1.InlineLinkedList.InlineListNode

/**
 * Switches between two lists and provides certain lookup and swapping operations.
 *
 * Holds two [InlineLinkedList]s, an active and a staging. The only way to append to the list is
 * to call [retainOrCreate]. This will look for the first item matching the predicate in the active
 * list and move it to the staging list (an [InlineListNode] can only be in one list at a time), or
 * create a new item and add it to the staging list if not found in the active list.
 *
 * At any time, to replace the active list with the staging list, call [commitStaging]
 * to swap the lists and clear the old active list. On commit, all items in the old active list will
 * be passed to the lambda passed to [commitStaging].
 */
public class ActiveStagingList<T : InlineListNode<T>> {

  /**
   * When not in the middle of a render pass, this list represents the active child workflows.
   * When in the middle of a render pass, this represents the list of children that may either
   * be re-rendered, or destroyed after the render pass is finished if they weren't re-rendered.
   *
   * During rendering, when a child is rendered, if it exists in this list it is removed from here
   * and added to [staging].
   */
  public var active: InlineLinkedList<T> = InlineLinkedList<T>()

  /**
   * When not in the middle of a render pass, this list is empty.
   * When rendering, every child that gets rendered is added to this list (possibly moved over from
   * [active]).
   * When [commitStaging] is called, this list is swapped with [active] and the old active list is
   * cleared.
   */
  public var staging: InlineLinkedList<T> = InlineLinkedList<T>()

  /**
   * Looks for the first item matching [predicate] in the active list and moves it to the staging
   * list if found, else creates and appends a new item.
   */
  internal inline fun retainOrCreate(
    predicate: (T) -> Boolean,
    create: () -> T
  ): T {
    val staged = active.removeFirst(predicate) ?: create()
    staging += staged
    return staged
  }

  /**
   * Looks for the first item matching [predicate] in the active list and removes it from the active
   * list. Then puts [child] into the staging list.
   */
  public inline fun removeAndStage(
    predicate: (T) -> Boolean,
    child: T?
  ) {
    active.removeFirst(predicate)
    child?.let {
      staging += it
    }
  }

  /**
   * Returns a reference to the first item matching [predicate] in the active list, or null if
   * not found.
   */
  public inline fun firstActiveOrNull(
    predicate: (T) -> Boolean
  ): T? = active.firstOrNull(predicate)

  /**
   * Swaps the active and staging list and clears the old active list, passing items in the
   * old active list to [onRemove].
   */
  public inline fun commitStaging(onRemove: (T) -> Unit) {
    // Any children left in the previous active list after the render finishes were not re-rendered
    // and must be torn down.
    active.forEach(onRemove)

    // Swap the lists and clear the staging one.
    val newStaging = active
    active = staging
    staging = newStaging
    staging.clear()
  }

  /**
   * Iterates over the active list.
   */
  internal inline fun forEachActive(block: (T) -> Unit) = active.forEach(block)

  /**
   * Iterates over the staging list.
   */
  public inline fun forEachStaging(block: (T) -> Unit): Unit = staging.forEach(block)
}
