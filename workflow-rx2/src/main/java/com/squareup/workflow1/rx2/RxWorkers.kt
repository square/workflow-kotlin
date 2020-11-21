package com.squareup.workflow1.rx2

import com.squareup.workflow1.Worker
import com.squareup.workflow1.asWorker
import io.reactivex.BackpressureStrategy.BUFFER
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx2.await
import org.reactivestreams.Publisher

/**
 * Creates a [Worker] from this [Observable].
 *
 * The [Observable] will be subscribed to when the [Worker] is started, and disposed when it is
 * cancelled.
 *
 * RxJava doesn't allow nulls, but it can't express that in its types. The receiver type parameter
 * is nullable so that the resulting [Worker] is non-nullable instead of having
 * platform nullability.
 */
public inline fun <reified T : Any> Observable<out T?>.asWorker(): Worker<T> =
  this.toFlowable(BUFFER).asWorker()

/**
 * Creates a [Worker] from this [Publisher] ([Flowable] is a [Publisher]).
 *
 * The [Publisher] will be subscribed to when the [Worker] is started, and cancelled when it is
 * cancelled.
 *
 * RxJava doesn't allow nulls, but it can't express that in its types. The receiver type parameter
 * is nullable so that the resulting [Worker] is non-nullable instead of having
 * platform nullability.
 */
public inline fun <reified T : Any> Publisher<out T?>.asWorker(): Worker<T> =
// This cast works because RxJava types don't actually allow nulls, it's just that they can't
  // express that in their types because Java.
  @Suppress("UNCHECKED_CAST")
  (this as Publisher<T>).asFlow().asWorker()

/**
 * Creates a [Worker] from this [Maybe].
 *
 * The [Maybe] will be subscribed to when the [Worker] is started, and disposed when it is
 * cancelled.
 *
 * RxJava doesn't allow nulls, but it can't express that in its types. The receiver type parameter
 * is nullable so that the resulting [Worker] is non-nullable instead of having
 * platform nullability.
 */
public inline fun <reified T : Any> Maybe<out T?>.asWorker(): Worker<T> =
  Worker.fromNullable { await() }

/**
 * Creates a [Worker] from this [Single].
 *
 * The [Single] will be subscribed to when the [Worker] is started, and disposed when it is
 * cancelled.
 *
 * RxJava doesn't allow nulls, but it can't express that in its types. The receiver type parameter
 * is nullable so that the resulting [Worker] is non-nullable instead of having
 * platform nullability.
 */
public inline fun <reified T : Any> Single<out T?>.asWorker(): Worker<T> =
// This !! works because RxJava types don't actually allow nulls, it's just that they can't
  // express that in their types because Java.
  Worker.from { await()!! }

/**
 * Creates a [Worker] from this [Completable].
 *
 * The [Completable] will be subscribed to when the [Worker] is started, and disposed when it is
 * cancelled.
 *
 * The key is required for this operator because there is no type information available to
 * distinguish workers.
 */
public fun Completable.asWorker(): Worker<Nothing> = Worker.createSideEffect { await() }
