package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Marker interface implemented by window-like renderings that map to a layer above
 * a base [Screen][com.squareup.workflow1.ui.Screen].
 *
 * An Overlay can be any part of the UI that visually floats in a layer above the main UI,
 * or above other Overlays. Possible examples include alerts, drawers, and tooltips.
 *
 * Note in particular that an Overlay is not necessarily a modal window.
 * Rendering types can opt into modality by extending [ModalOverlay].
 */
@WorkflowUiExperimentalApi
public interface Overlay
