package com.romansl.promise

internal class ThenFlattenSafeListener<in T>(private val promise: Promise<T>) : (Completed<T>) -> Unit {
    override fun invoke(completed: Completed<T>) {
        promise.state.getAndSet(completed).completeSafe(completed)
    }
}
