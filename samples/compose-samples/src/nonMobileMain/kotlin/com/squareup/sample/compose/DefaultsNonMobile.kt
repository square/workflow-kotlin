package com.squareup.sample.compose

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(isEnabled: Boolean, onBack: () -> Unit) = Unit
