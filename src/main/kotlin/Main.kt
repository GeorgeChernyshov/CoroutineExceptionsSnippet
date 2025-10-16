package org.example

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val processor = ReliableDataProcessor(this) // Use runBlocking's scope for testing

        // Subscribe to status updates
        launch {
            processor.status.collect { println("STATUS: $it") }
        }
        // Subscribe to processed results
        launch {
            processor.processedResults.collect {
                if (it.isNotEmpty()) println("RESULTS: $it")
            }
        }

        val itemsToProcess = listOf(
            DataItem(1, "Data1"),          // All OK
            DataItem(2, "Data2_INVALID"),   // Validation fails
            DataItem(3, "Data3"),          // MetaData fails, but proceeds
            DataItem(4, "Data4"),          // All OK
            DataItem(5, "Data5"),          // Store fails
            DataItem(6, "Data6_INVALID"),   // Validation fails
            DataItem(7, "Data7")           // All OK
        )

        processor.processBatch(itemsToProcess)

        // Give time for processing and status updates
        delay(3000)

        processor.clearResults()
        delay(500)
    }
}