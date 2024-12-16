package com.squareup.workflow1

import kotlin.reflect.KClass

internal actual fun commonUniqueClassName(kClass: KClass<*>): String {
  return kClass.qualifiedName ?: kClass.toString()
}
