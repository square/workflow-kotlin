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
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewEnvironment

@OptIn(WorkflowUiExperimentalApi::class)
class PoemListLayoutRunner(view: View) : LayoutRunner<PoemListRendering> {
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
    rendering: PoemListRendering,
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
    lateinit var rendering: PoemListRendering
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

  companion object : ViewFactory<PoemListRendering>
  by bind(
      R.layout.list,
      ::PoemListLayoutRunner
  )
}
