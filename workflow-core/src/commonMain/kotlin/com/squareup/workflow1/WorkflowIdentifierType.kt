package com.squareup.workflow1

import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Represents a subset of [KAnnotatedElement], namely [KClass] or [KType]. Used by the runtime to
 * determine whether a [WorkflowIdentifier], and thus the [Workflow] it identifies, is serializable
 * or not via the [Snapshot] mechanism.
 */
public sealed class WorkflowIdentifierType {

  public abstract val typeName: String

  /**
   * A [WorkflowIdentifier] is snapshottable if its type is this [Snapshottable] class.
   *
   * @constructor
   * @param typeName The qualified name of its corresponding [Workflow].
   * @param kClass The [KClass] of the [Workflow] this helps identify.
   */
  public data class Snapshottable(
    override val typeName: String,
    val kClass: KClass<*>? = null,
  ) : WorkflowIdentifierType() {
    public constructor(kClass: KClass<*>) : this(
      WorkflowIdentifierTypeNamer.uniqueName(kClass), kClass
    )
  }

  /**
   * A [WorkflowIdentifier] is unsnapshottable if its type is this [Unsnapshottable] class.
   *
   * @constructor
   * @param kType The [KType] of the [Workflow] this helps identify.
   */
  public data class Unsnapshottable(val kType: KType) : WorkflowIdentifierType() {
    init {
      require(kType.classifier is KClass<*>) {
        "Expected a KType with a KClass classifier, but was ${kType.classifier}"
      }
    }

    override val typeName: String = kType.toString()
  }
}

public expect object WorkflowIdentifierTypeNamer {
  public fun uniqueName(kClass: KClass<*>): String
}
