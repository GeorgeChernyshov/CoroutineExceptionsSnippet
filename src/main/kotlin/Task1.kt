package org.example

import kotlinx.coroutines.*

// Parent coroutine will not catch the child coroutines exceptions
// Since Child B catches CancellationException, it does not get cancelled
suspend fun task1() {
    val regularJob = Job()
    val regularScope = CoroutineScope(Dispatchers.Default + regularJob)

    val parentJob = regularScope.launch {
        try {
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
        catch (e: Exception) {
            println("Parent caught an exception")
        }
    }

    parentJob.join()
}