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

        val dProcessor = DocumentProcessor(this) // Use runBlocking's scope for testing

        // Collect processor status
        launch {
            dProcessor.processorStatus.collect { println("\nPROCESSOR STATUS: $it") }
        }
        // Collect individual document statuses
        launch {
            dProcessor.documentStatuses.collect { statuses ->
                if (statuses.isNotEmpty()) {
                    println("--- DOCUMENT STATUSES ---")
                    statuses.forEach { (id, status) -> println("  $id: $status") }
                }
            }
        }
        // Collect processed documents
        launch {
            dProcessor.processedDocuments.collect { docs ->
                if (docs.isNotEmpty()) {
                    println("\n--- FINAL PROCESSED DOCUMENTS ---")
                    docs.forEach { println("  $it") }
                }
            }
        }

        val documentIdsToProcess = listOf(
            "doc000", // All OK
            "doc001", // Fetch fails once, then retries successfully
            "doc002", // Fetch fails permanently (IOException)
            "doc003_error_parse", // ParsingException
            "doc004_critical_keyword_error", // Enrichment critical failure
            "doc005", // Validation failure
            "doc006", // Archiving failure
            "doc007_related_doc_timeout", // Enrichment optional timeout
            "doc008_optional_api_error" // Enrichment optional API error
        )

        dProcessor.startProcessingBatch(documentIdsToProcess)

        // Simulate user cancellation after some time (e.g., to see partial results)
        delay(2500) // Adjust delay as needed
        println("--- SIMULATING USER CANCELLATION ---")
        dProcessor.cancelAllProcessing()


        delay(5000) // Give time for cancellation to propagate and for remaining tasks to complete cleanup
        println("\n--- CLEARING RESULTS ---")
        processor.clearResults()
        delay(1000)
    }
}