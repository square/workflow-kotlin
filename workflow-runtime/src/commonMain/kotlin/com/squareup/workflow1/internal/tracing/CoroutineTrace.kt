package com.squareup.workflow1.internal.tracing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.DeprecationLevel.ERROR
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * Models a curated call stack from some root, up to this Trace node. In practice, this should be
 * inserted into a [CoroutineContext] as early as possible.
 *
 * This class this leverages [CoroutineContext.Element] in order to avoid passing a 'TraceContext'
 * around. Trace nodes are stored in the [CoroutineContext]. This means that traces can only be
 * started or added to from within a coroutine.
 *
 * The simplest way to add to a trace is to make the class implement [HasTraceTags] to provide
 * static tags, then use one of the [traced] extensions at the trace site.
 *
 * ```
 * class SomeClass : HasTraceTags {
 *   override val traceTags = listOf(SomeClass::class)
 *
 *   suspend fun doSomethingImportant(someArgument: String) {
 *     traced(someArgument) { /* anything happening here is wrapped in the new Trace */ }
 *   }
 * }
 * ```
 */
public sealed class Trace(
  public val tags: List<String>
) : CoroutineContext.Element {

  var beforeTrace: Any? = null
  var afterTrace: Any? = null

  init {
    check(tags.isNotEmpty()) {
      "You must provide at least one tag when creating a ${Trace::class.simpleName}."
    }
  }

  override val key: CoroutineContext.Key<*> get() = Key

  public abstract val depth: Int

  /**
   * ```
   * <ROOT OF TRACE>
   * tags: [Weather Underground]
   * └─ tags: [Kitchen]  --  args: [spaghetti]
   *    └─ tags: [Computer]  --  args: [Website(name=reddit.com)]
   *       └─ tags: [Garage]  --  args: [bike]
   *          └─ tags: [Oak Leaf Trail]  --  args: []
   *             └─ tags: [home]  --  args: [shower]
   * ```
   */
  public abstract fun asString(): String

  internal fun child(
    tags: Iterable<Any>,
    args: Iterable<Any>
  ): Trace = Child(
    parent = this,
    depth = depth + 1,
    tags = tags.traceStrings(),
    args = args.traceStrings()
  )

  internal fun child(
    vararg tags: Any,
    args: Iterable<Any>
  ): Trace = Child(
    parent = this,
    depth = depth + 1,
    tags = tags.traceStrings(),
    args = args.traceStrings()
  )

  private class Root(tags: List<String>) : Trace(tags) {
    override val depth = 0
    override fun asString(): String = buildString {
      appendLine("<ROOT OF TRACE>")
      append("tags: $tags")
    }
  }

  private class Child(
    val parent: Trace,
    override val depth: Int,
    tags: List<String>,
    val args: List<String>
  ) : Trace(tags) {

    override fun asString(): String = StringBuilder(parent.asString())
      .apply {
        val indent = "   ".repeat(depth - 1)

        append("\n$indent└─ tags: $tags  --  args: $args")
      }
      .toString()
  }

  public companion object Key : CoroutineContext.Key<Trace> {
    /** Creates a new [Trace] root. Prefer adding to an existing trace via a [traced] extension. */
    public fun start(vararg tags: Any): Trace = Root(tags.traceStrings())

    private fun Array<out Any>.traceStrings(): List<String> = map { it.traceString() }
    private fun Iterable<Any>.traceStrings(): List<String> = map { it.traceString() }
    private fun Any.traceString() = when (this) {
      is String -> this
      is KClass<*> -> simpleName ?: "--"
      is Class<*> -> simpleName ?: "--"
      else -> toString()
    }
  }
}

/**
 * Convenience interface for providing static tags to a [Trace], then consuming them via a [traced]
 * extension.
 *
 * ```
 * class MyCache(override val tags: List<Any>) : HasTraceTags {
 *
 *   suspend fun doSomethingImportant(someArgument: String) {
 *     traced(someArgument) { /* anything happening here is wrapped in the new Trace */ }
 *   }
 * }
 *
 * val cache = MyCache(listOf(MyCache::class, project))
 * ```
 */
public interface HasTraceTags {
  public val tags: Iterable<Any>
}

/**
 * Creates a [Trace] child node from outside a [HasTraceTags] implementation.
 *
 * The difference between the two parameters is that tags essentially describe the receiver of the
 * trace (the class, any identifying immutable instance properties like project path, etc.), and the
 * arguments are just like function arguments.
 *
 * @param tags the [Trace.tags] added to this trace child
 * @param args the dynamic runtime [Trace.Child.args] arguments added to this trace
 * @param block performed with the updated trace
 * @throws IllegalArgumentException if the [currentCoroutineContext] does not have a [Trace]
 */
public suspend fun <T> traced(
  tags: Iterable<Any>,
  args: Iterable<Any>,
  block: suspend CoroutineScope.() -> T
): T = tracedInternal(tags, args, block)

/**
 * Don't use. This overload exists in order to prevent accidentally providing the wrong tags to a
 * [Trace] from inside a [HasTraceTags]. If you need to provide runtime.
 */
@Deprecated(
  message = "Don't provide dynamic tags from inside HasTraceTags.",
  level = ERROR,
  replaceWith = ReplaceWith("traced(args, block)")
)
public suspend fun <T> HasTraceTags.traced(
  @Suppress("UNUSED_PARAMETER")
  tags: Iterable<Any>,
  args: Iterable<Any>,
  block: suspend CoroutineScope.() -> T
): T = traced(args, block)

/**
 * Creates a [Trace] child node from inside a [HasTraceTags] implementation.
 *
 * If you're sure you need to provide [Trace.tags] arguments as well, then remove the [HasTraceTags]
 * implementation from the receiver.
 *
 * @param args the dynamic runtime [Trace.Child.args] arguments added to this trace
 * @param block performed with the updated trace
 * @throws IllegalArgumentException if the [currentCoroutineContext] does not have a [Trace]
 */
public suspend fun <T> HasTraceTags.traced(
  args: Iterable<Any>,
  block: suspend CoroutineScope.() -> T
): T = tracedInternal(tags, args, block)

/**
 * Creates a [Trace] child node from inside a [HasTraceTags] implementation.
 *
 * If you're sure you need to provide [Trace.tags] arguments as well, then remove the [HasTraceTags]
 * implementation from the receiver.
 *
 * @param args the dynamic runtime [Trace.Child.args] arguments added to this trace
 * @param block performed with the updated trace
 * @throws IllegalArgumentException if the [currentCoroutineContext] does not have a [Trace]
 */
public suspend fun <T> HasTraceTags.traced(
  vararg args: Any,
  block: suspend CoroutineScope.() -> T
): T = tracedInternal(tags, args.toList(), block)

@OptIn(ExperimentalContracts::class)
private suspend fun <T> tracedInternal(
  tags: Iterable<Any>,
  args: Iterable<Any>,
  block: suspend CoroutineScope.() -> T
): T {
  contract {
    callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
  }

  val oldTrace = traceOrNull()
  // If the Trace doesn't already exist in the context, it must be disabled.
  // In that case, just make this a no-op
    ?: return coroutineScope { block() }

  val newTrace = oldTrace.child(tags, args)
  return withContext(newTrace, block)
}

/**
 * Unsafe-ish extension for extracting a [Trace] from inside a coroutine.
 *
 * @throws IllegalArgumentException if the [currentCoroutineContext] does not have a [Trace]
 * @see requireTrace
 */
public suspend fun trace(): Trace = currentCoroutineContext().requireTrace()

/** @return a [Trace] from inside a coroutine if it exists, else null */
internal suspend fun traceOrNull(): Trace? = currentCoroutineContext()[Trace]

/**
 * Unsafe-ish extension for extracting a [Trace] from inside a coroutine.
 *
 * This will throw if attempting to do any sort of tracing from inside a no-context
 * [runBlocking][kotlinx.coroutines.runBlocking] call. If it's necessary to use
 * `runBlocking`, the parent trace must be passed in as a `coroutineContext] argument.
 *
 * ```
 * suspend function doSomething() {
 *   // extract the trace from the existing context.
 *   val trace = coroutineContext.requireTrace()
 *
 *   // Sequence.forEach { ... } is an example of a lambda which doesn't suspend
 *   // or provide a CoroutineScope
 *   getSomeSequence().forEach {
 *     // now if we want to do suspending work, we need runBlocking
 *     // manually pass in the trace from the parent coroutine
 *     runBlocking(trace) {
 *       // now the trace continues
 *     }
 *   }
 * }
 * ```
 *
 * @throws IllegalArgumentException if the [currentCoroutineContext] does not have a [Trace]
 * @see trace
 */
public fun CoroutineContext.requireTrace(): Trace = requireNotNull(get(Trace)) {
  "This coroutineContext doesn't have a ${Trace::class.simpleName} in it -- $this"
}
