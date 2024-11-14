package com.squareup.workflow1

import kotlin.reflect.KClass

internal actual object CommonKClassTypeNamer {
  // Stores mappings between KClass instances and their assigned names.
  val mappings = mutableMapOf<KClass<*>, String>()

  // Note: This implementation does not differentiate between generic workflows.
  // (ie. SomeGenericWorkflow<String> and SomeGenericWorkflow<Int> would both return back the same
  // value.)
  //
  // Recommended workarounds:
  // - Always provide a key for generic workflows
  // - Create non-generic subclasses of generic workflows
  public actual fun uniqueName(kClass: KClass<*>): String {
    // Note: `kClass.qualifiedName` cannot be used here like other platforms as it's not supported
    // for JS. Therefore, we construct a unique name of each static KClass based on its simple name
    // and an index of when it was encountered.

    val mapping = mappings[kClass]
    if (mapping != null) {
      return mapping
    }

    // `simpleName` does not differentiate between generic workflows.
    val identifier = "${kClass.simpleName ?: kClass.hashCode()}(${mappings.size})"
    mappings[kClass] = identifier
    return identifier
  }
}
