package com.bumble.appyx.core.navigation.transition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.movableContentWithReceiverOf
import com.bumble.appyx.core.node.LocalMovableContentMap
import com.bumble.appyx.core.node.LocalNodeTargetVisibility


/**
 * Returns movable content for the given key. To reuse composable across Nodes movable content
 * must to be invoked only once at any time, therefore we should return null for the case when the
 * Node is transitioning to an invisible target and return movable content only if the targetState
 * is visible.
 *
 * Example: ParentNode (P) has BackStack with one Active Node (A). We push Node (B) to the BackStack,
 * and we want to move content from Node (A) to Node (B). Node (A) is transitioning from Active to
 * Stashed (invisible) state and Node (B) is transitioning from Created to Active (visible) state.
 * When this transition starts this function will return null for Node (A) and movable content for
 * Node (B)so that this content will be moved from Node (A) to Node (B).
 *
 * If you have a custom NavModel keep in mind that you can only move content from a visible Node
 * that becomes invisible to a Node that is becoming visible.
 */
@Composable
fun localMovableContentWithTargetVisibility(
    key: Any,
    content: @Composable () -> Unit
): (@Composable () -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentOf {
            content()
        }
    } as? @Composable () -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <P> localMovableContentWithTargetVisibility(
    key: Any,
    content: @Composable (P) -> Unit
): (@Composable (P) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentOf<P> {
            content(it)
        }
    } as? @Composable (P) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}


@Composable
fun <P1, P2> localMovableContentWithTargetVisibility(
    key: Any,
    content: @Composable (P1, P2) -> Unit
): (@Composable (P1, P2) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentOf<P1, P2> { p1, p2 ->
            content(p1, p2)
        }
    } as? @Composable (P1, P2) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <P1, P2, P3> localMovableContentWithTargetVisibility(
    key: Any,
    content: @Composable (P1, P2, P3) -> Unit
): (@Composable (P1, P2, P3) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentOf<P1, P2, P3> { p1, p2, p3 ->
            content(p1, p2, p3)
        }
    } as? @Composable (P1, P2, P3) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <P1, P2, P3, P4> localMovableContentWithTargetVisibility(
    key: Any,
    content: @Composable (P1, P2, P3, P4) -> Unit
): (@Composable (P1, P2, P3, P4) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentOf<P1, P2, P3, P4> { p1, p2, p3, p4 ->
            content(p1, p2, p3, p4)
        }
    } as? @Composable (P1, P2, P3, P4) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <R> localMovableContentWithReceiverAndTargetVisibility(
    key: Any,
    content: @Composable R.() -> Unit
): (@Composable R.() -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentWithReceiverOf<R> {
            this.content()
        }
    } as? @Composable R.() -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <R, P> localMovableContentWithReceiverAndTargetVisibility(
    key: Any,
    content: @Composable R.(P) -> Unit
): (@Composable R.(P) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentWithReceiverOf<R, P> { p ->
            this.content(p)
        }
    } as? @Composable R.(P) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <R, P1, P2> localMovableContentWithReceiverAndTargetVisibility(
    key: Any,
    content: @Composable R.(P1, P2) -> Unit
): (@Composable R.(P1, P2) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentWithReceiverOf<R, P1, P2> { p1, p2 ->
            this.content(p1, p2)
        }
    } as? @Composable R.(P1, P2) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
fun <R, P1, P2, P3> localMovableContentWithReceiverAndTargetVisibility(
    key: Any,
    content: @Composable R.(P1, P2, P3) -> Unit
): (@Composable R.(P1, P2, P3) -> Unit)? {
    if (!LocalNodeTargetVisibility.current) return null
    val movableContentMap = retrieveMovableContentMap()
    return movableContentMap.getOrPut(key) {
        movableContentWithReceiverOf<R, P1, P2, P3> { p1, p2, p3 ->
            this.content(p1, p2, p3)
        }
    } as? @Composable R.(P1, P2, P3) -> Unit ?: throw IllegalStateException(
        "Movable content for key $key is not of the expected type." +
                " The same $key has been used for different types of content."
    )
}

@Composable
private fun retrieveMovableContentMap(): MutableMap<Any, Any> {
    return requireNotNull(LocalMovableContentMap.current) {
        "LocalMovableContentMap not found in the composition hierarchy." +
                " Please use withMovableContent = true on Children composable."
    }
}

