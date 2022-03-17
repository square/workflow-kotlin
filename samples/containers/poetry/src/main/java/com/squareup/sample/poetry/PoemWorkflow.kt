package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemWorkflow.ClosePoem
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Workflow

/**
 * Renders a [Poem] as a [OverviewDetailScreen], whose overview is a [StanzaListRendering]
 * for the poem, and whose detail traverses through [StanzaRendering]s.
 *
 * (Defining this as an interface allows us to use other implementations
 * in other contexts -- check out our :benchmarks module!)
 */
interface PoemWorkflow : Workflow<Poem, ClosePoem, OverviewDetailScreen> {
  object ClosePoem
}
