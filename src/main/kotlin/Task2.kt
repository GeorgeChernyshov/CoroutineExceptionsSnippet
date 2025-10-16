package org.example

import kotlinx.coroutines.*

// Child A does not get cancelled since it catches CancellationExceptions
suspend fun task2() {
    val regularJob = Job()
    val regularScope = CoroutineScope(Dispatchers.Default + regularJob)

    val parentJob = regularScope.launch {
        val childA = launch {
            try {
                println("Child A started")
                delay(400)
            }
            catch (e: CancellationException) {
                println("Child A: Caught the CancellationException")
            }

            println("Child A completed")
        }

        val childB = launch {
            println("Child B started")
            delay(400)
            println("Child B completed")
        }

        launch {
            println("Child C started")
            delay(100)
            println("Cancelling child A")
            childA.cancel()
            println("Cancelling child B")
            childB.cancel()
            println("Child C completed")
        }
    }

    parentJob.join()
}