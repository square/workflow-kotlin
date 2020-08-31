/*
 * Copyright 2019 Square Inc.
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
package com.squareup.sample.stubvisibility

import android.view.Gravity.CENTER
import android.view.View
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.ClickyText
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.Outer
import com.squareup.sample.stubvisibility.databinding.StubVisibilityLayoutBinding
import com.squareup.workflow1.ui.BuilderBinding
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering

@OptIn(WorkflowUiExperimentalApi::class)
val StubVisibilityViewFactory: ViewFactory<Outer> =
  LayoutRunner.bind(StubVisibilityLayoutBinding::inflate) { rendering, env ->
    shouldBeFilledStub.update(rendering.top, env)
    shouldBeWrappedStub.update(rendering.bottom, env)
  }

@OptIn(WorkflowUiExperimentalApi::class)
val ClickyTextViewFactory: ViewFactory<ClickyText> = BuilderBinding(
    type = ClickyText::class,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      TextView(context).also { textView ->
        textView.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        textView.gravity = CENTER

        textView.bindShowRendering(initialRendering, initialEnv) { clickyText, _ ->
          textView.text = clickyText.message
          textView.isVisible = clickyText.visible
          textView.setOnClickListener(
              clickyText.onClick?.let { oc -> OnClickListener { oc() } }
          )
        }
      }
    }
)

private var View.isVisible: Boolean
  get() = visibility == VISIBLE
  set(value) {
    visibility = if (value) VISIBLE else GONE
  }
