package com.romansl.promise

import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.platform.platformStatic

@suppress("UNCHECKED_CAST")
public class Promise<out T> internal constructor(initState: State<T>) {
    internal val state: AtomicReference<State<Any>> = AtomicReference(initState)

    public fun <Result> then(continuation: Completed<T>.() -> Result): Promise<Result> {
        return (state.get() as State<T>).immediateThen(continuation)
    }

    public fun <Result> then(executor: Executor, continuation: Completed<T>.() -> Result): Promise<Result> {
        val promise = Promise<Result>(Pending())
        (state.get() as State<T>).then(promise, continuation, executor)
        return promise
    }

    public fun <Result> after(continuation: Completed<T>.() -> Promise<Result>): Promise<Result> {
        val promise = Promise<Result>(Pending())
        (state.get() as State<T>).immediateAfter(promise, continuation)
        return promise
    }

    public fun <Result> after(executor: Executor, continuation: Completed<T>.() -> Promise<Result>): Promise<Result> {
        val promise = Promise<Result>(Pending())
        (state.get() as State<T>).after(promise, continuation, executor)
        return promise
    }

    public fun isPending(): Boolean = state.get() is Pending

    inline
    public fun isPending(body: () -> Unit): Promise<T> {
        if (isPending()) {
            body()
        }

        return this
    }

    public fun getResult(): T = (state.get() as Completed<T>).result

    companion object {
        platformStatic
        public fun <Result> create(): Completion<Result> = Completion(Promise(Pending()))

        platformStatic
        public fun <Result> succeeded(value: Result): Promise<Result> = Promise(Succeeded(value))

        platformStatic
        public fun <Result> failed(error: Exception): Promise<Result> = Promise(Failed(error))

        platformStatic
        public fun <Result> call(callable: () -> Result): Promise<Result> {
            return try {
                succeeded(callable())
            } catch (e: Exception) {
                failed(e)
            }
        }

        platformStatic
        public fun <Result> call2(callable: () -> Promise<Result>): Promise<Result> {
            return try {
                val tcs = Promise.create<Result>()
                callable().thenComplete(tcs)
                return tcs.promise
            } catch (e: Exception) {
                failed(e)
            }
        }

        platformStatic
        public fun <Result> call(executor: Executor, callable: () -> Result): Promise<Result> {
            val tcs = Promise.create<Result>()
            executor.execute {
                try {
                    tcs.setResult(callable())
                } catch (e: Exception) {
                    tcs.setError(e)
                }
            }
            return tcs.promise
        }

        platformStatic
        public fun <Result> call2(executor: Executor, callable: () -> Promise<Result>): Promise<Result> {
            val tcs = Promise.create<Result>()
            executor.execute {
                try {
                    callable().thenComplete(tcs)
                } catch (e: Exception) {
                    tcs.setError(e)
                }
            }
            return tcs.promise
        }

        platformStatic
        public fun whenAll(promises: Collection<Promise<*>>): Promise<*> {
            return whenAll(*promises.toTypedArray())
        }

        platformStatic
        public fun whenAll(vararg promises: Promise<*>): Promise<*> {
            if (promises.isEmpty()) {
                return succeeded(Unit)
            } else if (promises.size() == 1) {
                return promises.get(0)
            }

            val allFinished = create<Unit>()
            val causes = ArrayList<Exception>()
            val count = AtomicInteger(promises.size())

            for (promise in promises) {
                promise.then {
                    try {
                        result
                    } catch(e: Exception) {
                        synchronized(causes) {
                            causes.add(e)
                        }
                    }

                    if (count.decrementAndGet() == 0) {
                        if (causes.isEmpty()) {
                            allFinished.setResult(Unit)
                        } else {
                            allFinished.setError(if (causes.size() == 1) {
                                causes.get(0)
                            } else {
                               AggregateException("There were ${causes.size()} exceptions.",causes.toTypedArray())
                            })
                        }
                    }
                }
            }

            return allFinished.promise
        }
    }
}

public class AggregateException(message: String, public val causes: Array<Throwable>) : Exception(message, causes.get(0))

public fun <R> Promise<R>.thenComplete(completion: Completion<R>) {
    then {
        try {
            completion.setResult(result)
        } catch (e: Exception) {
            completion.setError(e)
        }
    }
}
