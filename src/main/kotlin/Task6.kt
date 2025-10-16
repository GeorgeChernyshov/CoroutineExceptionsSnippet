package org.example

import kotlinx.coroutines.*

// Task 4 rewritten to launch children from the new scope
// Now the exception gets handled and child D does not get cancelled
suspend fun task6() {
    coroutineScope {
        val supervisorJob = SupervisorJob()
        val supervisorScope = CoroutineScope(supervisorJob)
        val exceptionHandler = CoroutineExceptionHandler { context, exception ->
            val job = context[Job]
            println("--- Caught by CoroutineExceptionHandler ---")
            println("Exception from context with Job: $job")
            println("Caught exception: $exception")
            println("----------------------------------------")
        }

        val childCJob = supervisorScope.launch(exceptionHandler) {
            println("Child C started")
            delay(100)

            throw IllegalStateException()
        }

        val childDJob = supervisorScope.launch {
            try {
                println("Child D started")
                delay(400)
            }
            catch (e: CancellationException) {
                println("Child D: Caught the CancellationException")
            }

            println("Child D completed")
        }

        childCJob.join()
        childDJob.join()
    }
}