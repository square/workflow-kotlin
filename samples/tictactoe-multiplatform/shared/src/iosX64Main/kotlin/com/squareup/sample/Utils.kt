package com.squareup.sample

import co.touchlab.kermit.Kermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun getMainScope(): CoroutineScope = MainScope(Dispatchers.Default, Kermit())
