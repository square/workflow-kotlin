package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewableRendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Marker interface implemented by window-like renderings that map to a layer above
 * a base [Screen][com.squareup.workflow1.ui.Screen].
 *
 * Note that an Overlay is not necessarily a modal window, though that is how
 * they are used in [BodyAndModalsScreen]. An Overlay can be any part of the UI
 * that visually floats in a layer above the main UI, or above other Overlays.
 * Possible examples include alerts, drawers, and tooltips.
 *
 * Whether or not an Overlay's presence indicates that events are blocked from lower
 * layers is a separate concern.
 */
@WorkflowUiExperimentalApi
public interface Overlay : ViewableRendering
