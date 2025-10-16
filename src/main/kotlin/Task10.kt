package org.example

import kotlinx.coroutines.*

suspend fun task10() {
    val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        val job = context[Job]
        println("--- Caught by CoroutineExceptionHandler ---")
        println("Exception from context with Job: $job")
        println("Caught exception: $exception")
        println("----------------------------------------")
    }

    coroutineScope {
        supervisorScope {
            launch(exceptionHandler) {
                println("ScopeChild1 started")
                delay(100)

                throw CustomException()
            }

            launch {
                try {
                    println("ScopeChild2 started")
                    delay(400)
                    println("ScopeChild2 completed")
                }
                catch (ex: CancellationException) {
                    println("ScopeChild2 got a CancellationException")
                }
            }
        }
    }
}