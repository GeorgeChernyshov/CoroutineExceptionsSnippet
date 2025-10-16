package org.example

import kotlinx.coroutines.*

// Now we are using supervisor job for our scope
// This doesn't solve our problem since children should be direct descendants of the supervisor scope
suspend fun task4() {
    val supervisorJob = SupervisorJob()
    val supervisorScope = CoroutineScope(Dispatchers.Default + supervisorJob)

   val parentJob = supervisorScope.launch {
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

    parentJob.join()
}