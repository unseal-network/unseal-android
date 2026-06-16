package com.bumble.appyx.core.children

inline fun <reified T : Any> ChildAware<*>.whenChildAttached(
    noinline callback: ChildCallback<T>,
) {
    whenChildAttached(T::class, callback)
}

inline fun <reified T1 : Any, reified T2 : Any> ChildAware<*>.whenChildrenAttached(
    noinline callback: ChildrenCallback<T1, T2>,
) {
    whenChildrenAttached(T1::class, T2::class, callback)
}
