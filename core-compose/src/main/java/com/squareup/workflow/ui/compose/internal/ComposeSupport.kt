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

package com.squareup.workflow.ui.compose.internal

import android.content.Context
import android.view.ViewGroup
import androidx.compose.Composable
import androidx.compose.Composition
import androidx.compose.CompositionReference
import androidx.compose.FrameManager
import androidx.compose.Recomposer
import androidx.compose.compositionFor
import androidx.ui.core.AndroidOwner
import androidx.ui.node.UiComposer
import com.squareup.workflow.ui.compose.internal.ReflectionSupport.createWrappedContent
import com.squareup.workflow.ui.core.compose.R

private typealias WrappedComposition = Composition

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
  parent: CompositionReference?,
  content: @Composable() () -> Unit
): Composition {
  FrameManager.ensureStarted()
  val composeView: AndroidOwner =
    if (childCount > 0) {
      getChildAt(0) as? AndroidOwner
    } else {
      removeAllViews(); null
    } ?: AndroidOwner(context).also { addView(it.view, DefaultLayoutParams) }
  return doSetContent(context, composeView, Recomposer.current(), parent, content)
}

/**
 * This is almost an exact copy of the private `doSetContent` function in Compose, but
 * it also accepts a parent [CompositionReference].
 */
private fun doSetContent(
  context: Context,
  owner: AndroidOwner,
  recomposer: Recomposer,
  parent: CompositionReference?,
  content: @Composable() () -> Unit
): Composition {
  // val original = compositionFor(context, owner.root, recomposer)
  val original = compositionFor(
      container = owner.root,
      recomposer = recomposer,
      parent = parent,
      composerFactory = { slotTable, factoryRecomposer ->
        UiComposer(context, owner.root, slotTable, factoryRecomposer)
      }
  )

  val wrapped = owner.view.getTag(R.id.wrapped_composition_tag)
      as? WrappedComposition
  // ?: WrappedComposition(owner, original).also {
      ?: createWrappedContent(owner, original).also {
        owner.view.setTag(R.id.wrapped_composition_tag, it)
      }
  wrapped.setContent(content)
  return wrapped
}

private object ReflectionSupport {

  private val WRAPPED_COMPOSITION_CLASS = Class.forName("androidx.ui.core.WrappedComposition")

  private val WRAPPED_COMPOSITION_CTOR =
    WRAPPED_COMPOSITION_CLASS.getConstructor(AndroidOwner::class.java, Composition::class.java)
        .apply { isAccessible = true }

  fun createWrappedContent(
    owner: AndroidOwner,
    original: Composition
  ): WrappedComposition = WRAPPED_COMPOSITION_CTOR.newInstance(owner, original) as Composition
}
