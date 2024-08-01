package com.squareup.sample.poetry

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Overview
import com.squareup.sample.container.poetry.R
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.BackStackConfig
import com.squareup.workflow1.ui.navigation.BackStackConfig.CanGoBack
import com.squareup.workflow1.ui.navigation.setBackHandler

@OptIn(WorkflowUiExperimentalApi::class)
data class StanzaListScreen(
  val title: String,
  val subtitle: String,
  val firstLines: List<String>,
  val onStanzaSelected: (Int) -> Unit,
  val onExit: () -> Unit,
  val selection: Int = -1
) : AndroidScreen<StanzaListScreen> {
  override val viewFactory =
    ScreenViewFactory.fromLayout(R.layout.list, ::StanzaListLayoutRunner)
}

@OptIn(WorkflowUiExperimentalApi::class)
private class StanzaListLayoutRunner(view: View) : ScreenViewRunner<StanzaListScreen> {
  private val toolbar = view.findViewById<Toolbar>(R.id.list_toolbar)
  private val recyclerView = view.findViewById<RecyclerView>(R.id.list_body)
    .apply { layoutManager = LinearLayoutManager(context) }

  private val adapter = Adapter()

  @SuppressLint("NotifyDataSetChanged")
  override fun showRendering(
    rendering: StanzaListScreen,
    environment: ViewEnvironment
  ) {
    adapter.view = rendering
    adapter.environment = environment
    adapter.notifyDataSetChanged()
    if (recyclerView.adapter == null) recyclerView.adapter = adapter
    toolbar.title = rendering.title
    toolbar.subtitle = rendering.subtitle

    if (environment[BackStackConfig] == CanGoBack) {
      toolbar.setNavigationOnClickListener { rendering.onExit() }
      toolbar.setBackHandler(rendering.onExit)
    } else {
      toolbar.navigationIcon = null
      toolbar.setBackHandler {}
    }

    if (rendering.selection >= 0) recyclerView.scrollToPosition(rendering.selection)
  }

  private class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

  private class Adapter : RecyclerView.Adapter<ViewHolder>() {
    lateinit var view: StanzaListScreen
    lateinit var environment: ViewEnvironment

    override fun onCreateViewHolder(
      parent: ViewGroup,
      viewType: Int
    ): ViewHolder {
      val selectable = environment[OverviewDetailConfig] == Overview
      val layoutId = if (selectable) {
        R.layout.list_row_selectable
      } else {
        R.layout.list_row_unselectable
      }

      return ViewHolder(
        LayoutInflater.from(parent.context).inflate(layoutId, parent, false) as TextView
      )
    }

    override fun getItemCount(): Int {
      return view.firstLines.size
    }

    override fun onBindViewHolder(
      holder: ViewHolder,
      position: Int
    ) = with(holder.view) {
      text = view.firstLines[position]
      isSelected = view.selection == position
      setOnClickListener {
        view.onStanzaSelected(position)
      }
    }
  }
}
