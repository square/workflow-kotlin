package com.squareup.sample.poetry

import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.TabStopSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import android.widget.TextView.BufferType.SPANNABLE
import androidx.appcompat.widget.Toolbar
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Detail
import com.squareup.sample.container.poetry.R
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.container.BackStackConfig
import com.squareup.workflow1.ui.container.BackStackConfig.None

@OptIn(WorkflowUiExperimentalApi::class)
data class StanzaScreen(
  val title: String,
  val stanzaNumber: Int,
  val lines: List<String>,
  val onGoUp: () -> Unit,
  val onGoBack: (() -> Unit)? = null,
  val onGoForth: (() -> Unit)? = null
) : AndroidScreen<StanzaScreen>, Compatible {
  override val compatibilityKey = "$title: $stanzaNumber"

  override val viewFactory =
    ScreenViewRunner.bind(R.layout.stanza_layout, ::StanzaLayoutRunner)
}

@OptIn(WorkflowUiExperimentalApi::class)
private class StanzaLayoutRunner(private val view: View) : ScreenViewRunner<StanzaScreen> {
  private val tabSize = TypedValue
    .applyDimension(TypedValue.COMPLEX_UNIT_SP, 24f, view.resources.displayMetrics)
    .toInt()

  private val toolbar = view.findViewById<Toolbar>(R.id.stanza_toolbar)
    // Hack works around strange TransitionManager behavior until I figure it out properly.
    .apply { id = -1 }
  private val lines = view.findViewById<TextView>(R.id.stanza_lines)
  private val more = view.findViewById<TextView>(R.id.stanza_more)
  private val goBack = view.findViewById<TextView>(R.id.stanza_back)

  override fun showRendering(
    rendering: StanzaScreen,
    viewEnvironment: ViewEnvironment
  ) {
    if (viewEnvironment[OverviewDetailConfig] == Detail) {
      toolbar.title = "Stanza ${rendering.stanzaNumber}"
      toolbar.subtitle = null
    } else {
      toolbar.title = rendering.title
      toolbar.subtitle = "Stanza ${rendering.stanzaNumber}"
    }

    lines.setTabulatedText(rendering.lines)

    rendering.onGoForth
      ?.let {
        lines.setOnClickListener { it() }
        more.setOnClickListener { it() }
        more.visibility = View.VISIBLE
      }
      ?: run {
        lines.setOnClickListener(null)
        more.setOnClickListener(null)
        more.visibility = View.GONE
      }

    rendering.onGoBack
      ?.let {
        goBack.setOnClickListener { it() }
        goBack.visibility = View.VISIBLE
      }
      ?: run {
        goBack.setOnClickListener(null)
        goBack.visibility = View.INVISIBLE
      }

    if (viewEnvironment[OverviewDetailConfig] != Detail &&
      viewEnvironment[BackStackConfig] != None
    ) {
      toolbar.setNavigationOnClickListener { rendering.onGoUp.invoke() }
    } else {
      toolbar.navigationIcon = null
    }

    view.backPressedHandler = rendering.onGoBack
      ?: rendering.onGoUp.takeIf { viewEnvironment[OverviewDetailConfig] != Detail }
  }

  private fun TextView.setTabulatedText(lines: List<String>) {
    val spans = SpannableStringBuilder()

    lines.forEach {
      if (spans.isNotEmpty()) spans.append("\n")
      val span = SpannableStringBuilder(it).apply {
        for (i in 1..5) {
          setSpan(TabStopSpan.Standard(tabSize * 1), 0, length, SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      }
      spans.append(span)
    }
    setText(spans, SPANNABLE)
  }
}
