package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Marker interface identifying [Overlay] renderings whose presence
 * indicates that events are blocked from lower layers.
 */
@WorkflowUiExperimentalApi
public interface ModalOverlay : Overlay
