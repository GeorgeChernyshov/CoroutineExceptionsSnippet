package org.example

import kotlinx.coroutines.*

class CustomException : Exception()

// Contrary to task 1, try/catch block actually catches the exception
suspend fun task9() {
    coroutineScope {
        try {
            coroutineScope {
                launch {
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
        catch (ex: Exception) {
            println("ParentCoroutine caught an Exception")
        }
    }
}