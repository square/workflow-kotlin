/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.Composable
import androidx.compose.Composition
import androidx.compose.CompositionReference
import androidx.compose.Recomposer
import androidx.compose.compositionFor
import androidx.lifecycle.LifecycleOwner
import androidx.ui.node.UiComposer
import com.squareup.workflow.ui.compose.ReflectionSupport.ANDROID_OWNER_CLASS
import com.squareup.workflow.ui.compose.ReflectionSupport.androidOwnerView
import com.squareup.workflow.ui.compose.ReflectionSupport.createOwner
import com.squareup.workflow.ui.compose.ReflectionSupport.createWrappedContent
import com.squareup.workflow.ui.compose.ReflectionSupport.ownerRoot
import com.squareup.workflow.ui.core.compose.R

private typealias AndroidOwner = Any
private typealias WrappedComposition = Composition
private typealias LayoutNode = Any

private val DefaultLayoutParams = ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.WRAP_CONTENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
)

/**
 * Copy of the built-in [setContent] function that takes an additional parent
 * [CompositionReference]. This will eventually be built-in to Compose, but until then this function
 * uses a bunch of reflection to access private Compose APIs.
 *
 * Once this ships in Compose, this whole file should be deleted.
 *
 * Tracked with Google [here](https://issuetracker.google.com/issues/156527485).
 * Note that ambient _changes_ also don't seem to get propagated currently, that bug is tracked
 * [here](https://issuetracker.google.com/issues/156527486).
 */
internal fun ViewGroup.setContent(
  recomposer: Recomposer,
  parent: CompositionReference,
  content: @Composable() () -> Unit
): Composition {
  val composeView: AndroidOwner =
    if (childCount > 0) {
      getChildAt(0).takeIf(ANDROID_OWNER_CLASS::isInstance)
    } else {
      removeAllViews(); null
    }
        ?: createOwner(context).also { addView(androidOwnerView(it), DefaultLayoutParams) }
  return doSetContent(context, composeView, recomposer, parent, content)
}

/**
 * This is almost an exact copy of the private `doSetContent` function in Compose, but
 * it also accepts a parent [CompositionReference].
 */
private fun doSetContent(
  context: Context,
  owner: AndroidOwner,
  recomposer: Recomposer,
  parent: CompositionReference,
  content: @Composable() () -> Unit
): Composition {
  // val original = compositionFor(context, owner.root, recomposer)
  val container = ownerRoot(owner)
  val original = compositionFor(
      container = container,
      recomposer = recomposer,
      parent = parent,
      composerFactory = { slotTable, factoryRecomposer ->
        UiComposer(context, container, slotTable, factoryRecomposer)
      }
  )

  val wrapped = androidOwnerView(owner).getTag(R.id.wrapped_composition_tag)
      as? WrappedComposition
  // ?: WrappedComposition(owner, original).also {
      ?: createWrappedContent(owner, original).also {
        androidOwnerView(owner).setTag(R.id.wrapped_composition_tag, it)
      }
  wrapped.setContent(content)
  return wrapped
}

private object ReflectionSupport {

  val ANDROID_OWNER_CLASS = Class.forName("androidx.ui.core.AndroidOwner")
  private val WRAPPED_COMPOSITION_CLASS = Class.forName("androidx.ui.core.WrappedComposition")
  private val ANDROID_OWNER_KT_CLASS = Class.forName("androidx.ui.core.AndroidOwnerKt")

  private val WRAPPED_COMPOSITION_CTOR =
    WRAPPED_COMPOSITION_CLASS.getConstructor(ANDROID_OWNER_CLASS, Composition::class.java)

  private val CREATE_OWNER_FUN =
    ANDROID_OWNER_KT_CLASS.getMethod("createOwner", Context::class.java, LifecycleOwner::class.java)
  private val ANDROID_OWNER_ROOT_GETTER = ANDROID_OWNER_CLASS.getMethod("getRoot")

  init {
    WRAPPED_COMPOSITION_CTOR.isAccessible = true
  }

  fun createOwner(context: Context): AndroidOwner =
    CREATE_OWNER_FUN.invoke(null, context, null) as AndroidOwner

  fun ownerRoot(owner: AndroidOwner): LayoutNode =
    ANDROID_OWNER_ROOT_GETTER.invoke(owner) as LayoutNode

  fun createWrappedContent(
    owner: AndroidOwner,
    original: Composition
  ): WrappedComposition = WRAPPED_COMPOSITION_CTOR.newInstance(owner, original) as Composition

  fun androidOwnerView(owner: AndroidOwner): View = owner as View
}
