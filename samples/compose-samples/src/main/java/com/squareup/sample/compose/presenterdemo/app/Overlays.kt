package com.squareup.sample.compose.presenterdemo.app

import com.squareup.sample.compose.presenterdemo.structurednav.OverlayViewModel
import com.squareup.workflow1.presenter.PresenterSlot

val DialogOverlaySlot = PresenterSlot<OverlayViewModel>("Dialog Overlay")
val SheetOverlaySlot = PresenterSlot<OverlayViewModel>("Sheet Overlay")
