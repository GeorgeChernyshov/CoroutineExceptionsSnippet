package org.example

import kotlinx.coroutines.*

// lets rewrite task2 but better
// In most cases, you don't need to create an explicit Job
// coroutineScope function is a suspending function so you don't need to call join()
suspend fun task5() {
    coroutineScope {
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
}