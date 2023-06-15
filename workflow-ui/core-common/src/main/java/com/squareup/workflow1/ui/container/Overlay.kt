package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Marker interface implemented by window-like renderings that map to a layer above
 * a base [Screen][com.squareup.workflow1.ui.Screen] by being placed in a
 * [BodyAndOverlaysScreen.overlays] list. See [BodyAndOverlaysScreen] for more details.
 *
 * An [Overlay] can be any window-like part of the UI that visually floats in a layer
 * above the main UI, or above other Overlays. Possible examples include alerts, drawers,
 * and tooltips.
 *
 * Note in particular that an [Overlay] is not necessarily a modal window -- that is,
 * one that prevents covered views and windows from processing UI events.
 * Rendering types can opt into modality by extending [ModalOverlay].
 *
 * See [ScreenOverlay] to define an [Overlay] whose content is provided by a wrapped
 * [Screen][com.squareup.workflow1.ui.Screen].
 */
@WorkflowUiExperimentalApi
public interface Overlay
