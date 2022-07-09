package com.squareup.workflow1

import kotlin.reflect.KClass

public actual object WorkflowIdentifierTypeNamer {
  // Stores mappings between KClass instances and their assigned names.
  val mappings = mutableMapOf<KClass<*>,String>()

  public actual fun uniqueName(kClass: KClass<*>): String {
    // Note: `kClass.qualifiedName` cannot be used here like other platforms as it's not supported
    // for JS. Therefore, we construct a unique name of each static KClass based on its simple name
    // and an index of when it was encountered.

    val mapping = mappings[kClass]
    if (mapping != null) {
      return mapping
    }

    val identifier = "${kClass.simpleName ?: kClass.hashCode()}(${mappings.size})"
    mappings[kClass] = identifier
    return identifier
  }
}
