package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Workflow

typealias RecursionGraphConfig = Pair<Int, Int>
typealias ConfigAndPoems = Pair<RecursionGraphConfig, List<Poem>>

/**
 * Provides an overview / detail treatment of a list of [Poem]s.
 *
 * (Defining this as an interface allows us to use other implementations
 * in other contexts -- check out our :benchmarks module!)
 */
interface PoemsBrowserWorkflow :
  Workflow<ConfigAndPoems, Unit, OverviewDetailScreen<*>>
