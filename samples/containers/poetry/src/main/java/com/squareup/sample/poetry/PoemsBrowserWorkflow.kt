package com.squareup.sample.poetry

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Workflow

/**
 * Provides an overview / detail treatment of a list of [Poem]s.
 *
 * (Defining this as an interface allows us to use other implementations
 * in other contexts -- check out our :benchmarks module!)
 */
interface PoemsBrowserWorkflow : Workflow<List<Poem>, Unit, OverviewDetailScreen>
