package com.squareup.workflow1

/**
 * [Nothing] cannot be used as a reified type so we have duplicated
 * it here to avoid adding a dependency on kotlin.reflect.
 */
internal class Void private constructor()
