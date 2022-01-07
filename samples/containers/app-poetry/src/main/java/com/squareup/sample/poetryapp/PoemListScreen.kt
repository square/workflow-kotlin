package com.squareup.sample.poetryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Overview
import com.squareup.sample.container.poetryapp.R
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class PoemListScreen(
  val poems: List<Poem>,
  val onPoemSelected: (Int) -> Unit,
  val selection: Int = -1
) : AndroidScreen<PoemListScreen> {
  override val viewFactory = ScreenViewFactory.ofLayout(
    R.layout.list
  ) { it: View -> PoemListLayoutUpdater(it) }
}

@OptIn(WorkflowUiExperimentalApi::class)
private class PoemListLayoutUpdater(view: View) : ScreenViewUpdater<PoemListScreen> {
  init {
    view.findViewById<Toolbar>(R.id.list_toolbar)
      .apply {
        title = view.resources.getString(R.string.poems)
        navigationIcon = null
      }
  }

  private val recyclerView = view.findViewById<RecyclerView>(R.id.list_body)
    .apply { layoutManager = LinearLayoutManager(context) }

  private val adapter = Adapter()

  override fun showRendering(
    rendering: PoemListScreen,
    viewEnvironment: ViewEnvironment
  ) {
    adapter.rendering = rendering
    adapter.environment = viewEnvironment
    adapter.notifyDataSetChanged()
    if (recyclerView.adapter == null) recyclerView.adapter = adapter

    if (rendering.selection >= 0) recyclerView.scrollToPosition(rendering.selection)
  }

  private class ViewHolder(val view: TextView) : RecyclerView.ViewHolder(view)

  private class Adapter : RecyclerView.Adapter<ViewHolder>() {
    lateinit var rendering: PoemListScreen
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
      return rendering.poems.size
    }

    override fun onBindViewHolder(
      holder: ViewHolder,
      position: Int
    ) = with(holder.view) {
      text = rendering.poems[position].title
      isActivated = rendering.selection == position
      setOnClickListener {
        rendering.onPoemSelected(position)
      }
    }
  }
}
