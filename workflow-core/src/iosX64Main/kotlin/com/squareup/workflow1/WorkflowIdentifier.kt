package com.squareup.workflow1

import okio.Buffer
import okio.ByteString
import okio.EOFException
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Represents a [Workflow]'s "identity" and is used by the runtime to determine whether a workflow
 * is the same as one that was rendered in a previous render pass, in which case its state
 * should be re-used; or if it's a new workflow and needs to be started.
 *
 * A workflow's identity consists primarily of its concrete type (i.e. the class that implements
 * the [Workflow] interface). Two workflows of the same concrete type are considered identical.
 * However, if a workflow class implements [ImpostorWorkflow], the identifier will also include
 * that workflow's [ImpostorWorkflow.realIdentifier].
 *
 * Instances of this class are [equatable][equals] and [hashable][hashCode].
 *
 * ## Identifiers and snapshots
 *
 * Since workflows can be [serialized][StatefulWorkflow.snapshotState], workflows' identifiers must
 * also be serializable in order to match workflows back up with their snapshots when restoring.
 * However, some [WorkflowIdentifier]s may represent workflows that cannot be snapshotted. When an
 * identifier is not snapshottable, [toByteStringOrNull] will return null, and any identifiers that
 * reference [ImpostorWorkflow]s whose [ImpostorWorkflow.realIdentifier] is not snapshottable will
 * also not be snapshottable. Such identifiers are created with [unsnapshottableIdentifier], but
 * should not be used to wrap arbitrary workflows since those workflows may expect to be
 * snapshotted.
 *
 * @constructor
 * @param type The [KClass] of the [Workflow] this identifier identifies, or the [KType] of an
 * [unsnapshottableIdentifier].
 * @param proxiedIdentifier An optional identifier from [ImpostorWorkflow.realIdentifier] that will
 * be used to further narrow the scope of this identifier.
 * @param description Implementation of [describeRealIdentifier].
 */
@ExperimentalWorkflowApi
public actual class WorkflowIdentifier internal constructor(
  private val typeName: String,
  private val isKClass: Boolean,
  private val proxiedIdentifier: WorkflowIdentifier? = null,
  private val description: (() -> String?)? = null
) {

  private val proxiedIdentifiers = generateSequence(this) { it.proxiedIdentifier }
  actual public companion object {
    private const val NO_PROXY_IDENTIFIER_TAG = 0.toByte()
    private const val PROXY_IDENTIFIER_TAG = 1.toByte()

    actual fun parse(bytes: ByteString): WorkflowIdentifier? = Buffer().let { source ->
      source.write(bytes)

      try {
        val typeString = source.readUtf8WithLength()
        val proxiedIdentifier = when (source.readByte()) {
          NO_PROXY_IDENTIFIER_TAG -> null
          PROXY_IDENTIFIER_TAG -> parse(source.readByteString())
          else -> throw IllegalArgumentException("Invalid WorkflowIdentifier")
        }
        val isKClass: Boolean = source.readBooleanFromInt()

        return WorkflowIdentifier(typeString, isKClass, proxiedIdentifier)
      } catch (e: EOFException) {
        throw IllegalArgumentException("Invalid WorkflowIdentifier")
      }
    }
  }
  /**
   * If this identifier is snapshottable, returns the serialized form of the identifier.
   * If it is not snapshottable, returns null.
   */
  public actual fun toByteStringOrNull(): ByteString? {
    if (!isKClass) {
      return null
    }

    val proxiedBytes = proxiedIdentifier?.let {
      // If we have a proxied identifier but it's not serializable, then we can't be serializable
      // either.
      it.toByteStringOrNull() ?: return null
    }

    return Buffer().let { sink ->
      sink.writeUtf8WithLength(typeName)
      if (proxiedBytes != null) {
        sink.writeByte(PROXY_IDENTIFIER_TAG.toInt())
        sink.write(proxiedBytes)
      } else {
        sink.writeByte(NO_PROXY_IDENTIFIER_TAG.toInt())
      }
      sink.readByteString()
    }
  }

  /**
   * If this identifier identifies an [ImpostorWorkflow], returns the result of that workflow's
   * [ImpostorWorkflow.describeRealIdentifier] method, otherwise returns a description of this
   * identifier including the name of its workflow type and any [ImpostorWorkflow.realIdentifier]s.
   *
   */
  override fun toString(): String =
    description?.invoke()
      ?: proxiedIdentifiers
        .joinToString { it.typeName }
        .let { "WorkflowIdentifier($it)" }

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is WorkflowIdentifier -> false
    else -> typeName == other.typeName && proxiedIdentifier == other.proxiedIdentifier
  }

  override fun hashCode(): Int {
    var result = typeName.hashCode()
    result = 31 * result + (proxiedIdentifier?.hashCode() ?: 0)
    return result
  }
}

/**
 * The [WorkflowIdentifier] that identifies this [Workflow].
 */
@ExperimentalWorkflowApi
public actual val Workflow<*, *, *>.identifier: WorkflowIdentifier
  get() {
    val maybeImpostor = this as? ImpostorWorkflow
    return WorkflowIdentifier(
      typeName = this::class.toString().removePrefix("class "),
      isKClass = false,
      proxiedIdentifier = maybeImpostor?.realIdentifier,
      description = maybeImpostor?.let { it::describeRealIdentifier }
    )
  }

/**
 * Creates a [WorkflowIdentifier] that is not capable of being snapshotted and will cause any
 * [ImpostorWorkflow] workflow identified by it to also not be snapshotted.
 *
 * **This function should not be used for [ImpostorWorkflow]s that wrap arbitrary workflows**, since
 * those workflows may expect to be on snapshotted. Using such identifiers _anywhere in the
 * [ImpostorWorkflow.realIdentifier] chain_ will disable snapshotting for that workflow. **This
 * function should only be used for [ImpostorWorkflow]s that wrap a closed set of known workflow
 * types.**
 */
@ExperimentalWorkflowApi
public actual fun unsnapshottableIdentifier(type: KType): WorkflowIdentifier = WorkflowIdentifier(type.toString(), false)
