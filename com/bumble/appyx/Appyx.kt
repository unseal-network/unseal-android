package com.bumble.appyx

import com.bumble.appyx.core.children.ChildEntry
import com.bumble.appyx.core.plugin.Plugin

object Appyx {

    var exceptionHandler: ((Exception) -> Unit)? = null
    var defaultChildKeepMode: ChildEntry.KeepMode = ChildEntry.KeepMode.KEEP

    /**
     * Plugins that are applied to all Nodes.
     */
    var globalPlugins: List<Plugin> = emptyList()

    fun reportException(exception: Exception) {
        val handler = exceptionHandler
        if (handler != null) {
            handler(exception)
        } else {
            throw exception
        }
    }

}
