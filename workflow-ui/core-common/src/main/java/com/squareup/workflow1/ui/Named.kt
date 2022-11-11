package com.squareup.workflow1.ui

import com.squareup.workflow1.visual.WithName

/**
 * Allows renderings that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [wrapped] fields.
 *
 * TODO: Note that we deleted `data` (required so that wrapped can be final),
 *  which is reasonable -- should never have been a
 *  data class in the first place. Land that change (eliminate all data uses) separately
 *  before dropping the bomb. Could have sworn we already had done that.
 */
@WorkflowUiExperimentalApi
@Deprecated("Use NamedScreen or NamedOverlay")
public class Named<W : Any>(
  wrapped: W,
  name: String
) : WithName<W>(wrapped, name)
