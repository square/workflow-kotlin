package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode

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
internal class ActiveStagingList<T : InlineListNode<T>>(
  private val identityOf: ((T) -> Any?)? = null,
) {

  /**
   * When not in the middle of a render pass, this list represents the active child workflows.
   * When in the middle of a render pass, this represents the list of children that may either
   * be re-rendered, or destroyed after the render pass is finished if they weren't re-rendered.
   *
   * During rendering, when a child is rendered, if it exists in this list it is removed from here
   * and added to [staging].
   */
  private var active = InlineLinkedList<T>()
  private var activeIdentities = identityOf?.let { mutableMapOf<Any?, T>() }

  /**
   * When not in the middle of a render pass, this list is empty.
   * When rendering, every child that gets rendered is added to this list (possibly moved over from
   * [active]).
   * When [commitStaging] is called, this list is swapped with [active] and the old active list is
   * cleared.
   */
  private var staging = InlineLinkedList<T>()
  private var stagingIdentities = identityOf?.let { mutableMapOf<Any?, T>() }

  /**
   * Looks for the first item matching [predicate] in the active list and moves it to the staging
   * list if found, else creates and appends a new item.
   */
  inline fun retainOrCreate(
    predicate: (T) -> Boolean,
    create: () -> T
  ): T {
    val staged = active.removeFirst(predicate) ?: create()
    val identity = identityOf?.invoke(staged)
    require(stagingIdentities?.containsKey(identity) != true) {
      "Expected identities to be unique in staging: \"$identity\""
    }
    activeIdentities?.remove(identity)
    staging += staged
    stagingIdentities?.set(identity, staged)
    return staged
  }

  /**
   * Retains a node from active by [identity] or creates a new one, then stages it.
   *
   * This API is only available when [identityOf] is configured.
   */
  inline fun retainOrCreateByIdentity(
    identity: Any?,
    create: () -> T,
  ): T {
    val identityOf = requireNotNull(identityOf) {
      "identityOf must be configured to call retainOrCreateByIdentity"
    }
    require(stagingIdentities?.containsKey(identity) != true) {
      "Expected identities to be unique in staging: \"$identity\""
    }

    val retained = activeIdentities?.remove(identity)
    val staged = retained ?: create()
    if (retained != null) {
      check(active.removeFirst { it === retained } != null) {
        "Expected retained node to still exist in active list."
      }
    }
    require(identityOf(staged) == identity) {
      "Expected retained identity \"${identityOf(
        staged
      )}\" to match requested identity \"$identity\""
    }
    staging += staged
    stagingIdentities?.set(identity, staged)
    return staged
  }

  /**
   * Returns true if [identity] exists in the active list.
   *
   * This API is only available when [identityOf] is configured.
   */
  fun containsActiveIdentity(identity: Any?): Boolean {
    check(identityOf != null) { "identityOf must be configured to query identities" }
    return activeIdentities?.containsKey(identity) == true
  }

  /**
   * Returns true if [identity] exists in the staging list.
   *
   * This API is only available when [identityOf] is configured.
   */
  fun containsStagingIdentity(identity: Any?): Boolean {
    check(identityOf != null) { "identityOf must be configured to query identities" }
    return stagingIdentities?.containsKey(identity) == true
  }

  /**
   * Swaps the active and staging list and clears the old active list, passing items in the
   * old active list to [onRemove].
   */
  inline fun commitStaging(onRemove: (T) -> Unit) {
    // Any children left in the previous active list after the render finishes were not re-rendered
    // and must be torn down.
    active.forEach { node ->
      onRemove(node)
      identityOf?.let { identityOf ->
        activeIdentities?.remove(identityOf(node))
      }
    }

    // Swap the lists and clear the staging one.
    val newStaging = active
    active = staging
    staging = newStaging
    val newStagingIdentities = activeIdentities
    activeIdentities = stagingIdentities
    stagingIdentities = newStagingIdentities
    staging.clear()
    stagingIdentities?.clear()
  }

  /**
   * Iterates over the active list.
   */
  inline fun forEachActive(block: (T) -> Unit) = active.forEach(block)

  /**
   * Iterates over the staging list.
   */
  inline fun forEachStaging(block: (T) -> Unit) = staging.forEach(block)
}
