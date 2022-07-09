package com.squareup.workflow1

import kotlin.reflect.KClass

internal actual object WorkflowIdentifierTypeNamer {
  public actual fun uniqueName(kClass: KClass<*>): String {
    return kClass.qualifiedName ?: kClass.toString()
  }
}
