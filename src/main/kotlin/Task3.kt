package org.example

import kotlinx.coroutines.*

// Exception Handler will catch the exceptions now
// Child B will still fail to cancel, ExceptionHandler only works on exceptions that propagate between levels
suspend fun task3() {
    val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        val job = context[Job]
        println("--- Caught by CoroutineExceptionHandler ---")
        println("Exception from context with Job: $job")
        println("Caught exception: $exception")
        println("----------------------------------------")
    }

    val regularJob = Job()
    val regularScope = CoroutineScope(
        Dispatchers.Default +
                regularJob +
                exceptionHandler
    )

    val parentJob = regularScope.launch {
        launch {
            println("Child A started")
            delay(100)
            println("Child A throwing an Exception")

            throw IllegalStateException()
        }

        launch {
            try {
                println("Child B started")
                delay(400)
            }
            catch (e: CancellationException) {
                println("Child B: Caught the CancellationException")
            }

            println("Child B completed")
        }
    }

    parentJob.join()
}