package com.squareup.workflow1.tracing

import com.squareup.workflow1.Worker
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowIdentifierType.Snapshottable
import com.squareup.workflow1.WorkflowIdentifierType.Unsnapshottable
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Short name that can be used for logs that has the [identifier] logged as well
 * as the key.
 */
public fun WorkflowSession.toWfLoggingName(): String {
  val renderKey = renderKey
  return if (renderKey.isEmpty()) {
    identifier.toWfLoggingName()
  } else {
    "${identifier.toWfLoggingName()}($renderKey)"
  }
}

/**
 * Short name that for the identifier that can be used for logs. This reports
 * the 'log name' of the class.
 */
public fun WorkflowIdentifier.toWfLoggingName(): String {
  return when (val type = realType) {
    is Snapshottable -> type.kClass?.toWfLoggingName() ?: type.typeName
    is Unsnapshottable -> type.kType.toWfLoggingName()
  }.run {
    wfRemoveReflectionNotAvailable()
  }
}

/**
 * Useful Action Logging with our utilities to remove extra strings as needed.
 */
public fun <P, S, O> WorkflowAction<P, S, O>.toLoggingShortName(): String {
  return debuggingName
    .wfRemoveReflectionNotAvailable()
    .wfStripSquarePackage()
}

/**
 * Same as [WorkflowAction<P, S, O>.toLoggingShortName()] but wraps it in "Action()" to help
 * identify what it is.
 */
public fun <P, S, O> WorkflowAction<P, S, O>.toWfLoggingName(): String {
  return "Action(${toLoggingShortName()})"
}

/**
 * Extract key for worker action names using knowledge of implementation details.
 */
public fun String.workerKey(): String {
  return if (contains(Worker.WORKER_OUTPUT_ACTION_NAME)) {
    substringAfter("key=").substringBefore(')')
  } else {
    ""
  }
}

/**
 * Reasonable log name based on type.
 */
public fun KType.toWfLoggingName(): String {
  if (classifier == null) return toString().wfStripSquarePackage()

  val classifierName = when (val c = classifier) {
    is KClass<*> -> c.toWfLoggingName()
    else -> toString().wfStripSquarePackage()
  }

  val params = arguments.map { projection ->
    when (val type = projection.type) {
      is KType -> type.toWfLoggingName()
      else -> "*"
    }
  }

  return if (params.isEmpty()) classifierName else "$classifierName<${params.joinToString(", ")}>"
}

/**
 * Gets a class's simple name. If an inner class, its wrapping class's simple name is added as a
 * prefix.
 *
 * For example, `java.util.Map` would be `Map`, and `java.util.Map.Entry` would be `Map.Entry`.
 */
public fun getWfHumanClassName(obj: Any): String {
  val objClass: Class<*> = when (obj) {
    is KClass<*> -> obj.java
    is Class<*> -> obj
    else -> obj.javaClass
  }
  var humanName = objClass.simpleName.takeIf { it.isNotBlank() }
    ?: objClass.name.substringAfterLast(".")

  var memberClass: Class<*>? = objClass
  while (memberClass?.isMemberClass == true) {
    val tempMemberClass: Class<*>? = memberClass.declaringClass?.also {
      memberClass = it
    }
    humanName = tempMemberClass?.simpleName + "." + humanName
  }
  return humanName
}

/**
 * Reasonable class name based on type.
 */
public fun KClass<*>.toWfLoggingName(): String {
  return getWfHumanClassName(this)
}

/**
 * Alternative to [toString] used by Workflow logging.
 */
public fun getWfLogString(log: Any?): String {
  return when (log) {
    null,
    is Boolean,
    is Enum<*>,
    is Number -> log.toString()

    is String -> log
    is Pair<*, *> -> "Pair(${getWfLogString(log.first)}, ${getWfLogString(log.second)})"
    is Triple<*, *, *> ->
      "Triple(${getWfLogString(log.first)}, ${
        getWfLogString(
          log.second
        )
      }, ${getWfLogString(log.third)})"

    is Loggable -> log.toLogString()
    is WorkflowAction<*, *, *> -> log.toWfLoggingName()

    else -> log::class.toWfLoggingName()
  }
}

/**
 * Returns an ellipsized string if this string is longer than [maxLength].
 *
 * @param maxLength The maximum length the string can be before ellipsizing will occur. This must be
 * a positive number.
 */
public fun String.wfEllipsizeEnd(maxLength: Int): String {
  require(maxLength > 0)

  return if (maxLength < length) {
    take(maxLength - 1).trimEnd().plus(Typography.ellipsis)
  } else {
    this
  }
}

/**
 * Removes the string from kotlin.jvm.internal.Reflection#REFLECTION_NOT_AVAILABLE
 */
public fun String.wfRemoveReflectionNotAvailable() = replace(
  " (Kotlin reflection is not available)",
  ""
)

/**
 * Returns the contents of the receiving string with all "com.squareup.*" packages
 * stripped out of it.
 *
 * This will help make things more readable for classes within this library.
 */
public fun String.wfStripSquarePackage(): String {
  // Find the index of every "com.squareup".
  var cursor = 0
  var packages: MutableList<Int>? = null
  do {
    val packageNameIndex = indexOf("com.squareup", cursor)
    if (packageNameIndex > -1) {
      if (packages == null) packages = ArrayList()
      packages.add(packageNameIndex)
      cursor = packageNameIndex + 1
    } else {
      break
    }
  } while (cursor < length)

  // None found, punt. Note that we haven't allocated anything yet, that's nice.
  if (packages == null) return this

  val builder: StringBuilder = StringBuilder()
  cursor = 0
  for (packageNameIndex in packages) {
    // Append everything before the next package
    builder.append(substring(cursor, packageNameIndex))

    // Skip the package name.
    cursor = packageNameIndex
    do {
      val c = this[cursor]
      if (c in 'a'..'z' || c in '0'..'9' || c == '.') {
        cursor++
      } else {
        break
      }
    } while (cursor < length)
  }

  // Append everything after the last package name.
  builder.append(substring(cursor))
  return builder.toString()
}
