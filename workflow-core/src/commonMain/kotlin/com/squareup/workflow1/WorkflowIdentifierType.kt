package com.squareup.workflow1

import kotlin.reflect.KClass
import kotlin.reflect.KType

public sealed class WorkflowIdentifierType {

  public abstract val typeName: String

  public data class Snapshottable(
    override val typeName: String,
    val kClass: KClass<*>? = null,
  ) : WorkflowIdentifierType() {
    public constructor(kClass: KClass<*>) : this(
      kClass.qualifiedName ?: kClass.toString(), kClass
    )
  }

  public data class Unsnapshottable(val kType: KType) : WorkflowIdentifierType() {
    init {
      require(kType.classifier is KClass<*>) {
        "Expected a KType with a KClass classifier, but was ${kType.classifier}"
      }
    }

    override val typeName: String = kType.toString()
  }
}
