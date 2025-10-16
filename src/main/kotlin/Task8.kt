package org.example

import kotlinx.coroutines.*

suspend fun task8() {
    val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        val job = context[Job]
        println("--- Caught by CoroutineExceptionHandler ---")
        println("Exception from context with Job: $job")
        println("Caught exception: $exception")
        println("----------------------------------------")
    }

    val parentJob = CoroutineScope(exceptionHandler).launch {
        launch {
            println("Coroutine launched via launch")
            delay(100)

            throw RuntimeException()
        }

        val deferred = async {
            println("Coroutine launched via async")
            delay(100)

            throw RuntimeException()
        }

        try {
            deferred.await()
        }
        catch (ex: RuntimeException) {
            println("Exception was not intercepted by the ExceptionHandler")
        }
    }

    parentJob.join()
}