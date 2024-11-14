package com.squareup.workflow1

import kotlin.reflect.KClass

internal actual object CommonKClassTypeNamer {
  public actual fun uniqueName(kClass: KClass<*>): String {
    return kClass.qualifiedName ?: kClass.toString()
  }
}
