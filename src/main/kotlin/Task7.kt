package org.example

import kotlinx.coroutines.*

// Task 6 rewritten to use supervisorScope
suspend fun task7() {
    supervisorScope {
        val exceptionHandler = CoroutineExceptionHandler { context, exception ->
            val job = context[Job]
            println("--- Caught by CoroutineExceptionHandler ---")
            println("Exception from context with Job: $job")
            println("Caught exception: $exception")
            println("----------------------------------------")
        }

        launch(exceptionHandler) {
            println("Child C started")
            delay(100)

            throw IllegalStateException()
        }

        launch {
            try {
                println("Child D started")
                delay(400)
            }
            catch (e: CancellationException) {
                println("Child D: Caught the CancellationException")
            }

            println("Child D completed")
        }
    }
}