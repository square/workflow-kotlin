package com.squareup.workflow1.internal

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * Note that we may want to use a different method for iOS since we are repeatedly calling this
 * within a very small amount of ms. See [here](https://stackoverflow.com/questions/358207/iphone-how-to-get-current-milliseconds)
 * where it notes that timeIntervalSince1970 might not differentiate when called repeatedly
 * within a few ms.
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
