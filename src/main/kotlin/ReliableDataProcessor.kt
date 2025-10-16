package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

data class DataItem(val id: Int, val payload: String)

object ExternalService {
    suspend fun fetchMetaData(id: Int): String {
        delay(150) // Simulate network call
        if (id % 3 == 0)
            throw IOException("Failed to fetch metadata for ID $id")

        if (id == 4)
            throw CustomException()

        return "Meta-$id"
    }

    suspend fun validateData(payload: String): Boolean {
        delay(100) // Simulate validation logic
        if (payload.contains("INVALID"))
            throw IllegalArgumentException("Invalid payload: $payload")

        return true
    }

    suspend fun storeResult(id: Int, result: String): String {
        delay(200) // Simulate database write
        if (id % 5 == 0)
            throw RuntimeException("Database error storing result for ID $id")

        return "Stored-$id"
    }
}

class ReliableDataProcessor(
    private val scope: CoroutineScope // Inject a CoroutineScope, e.g., viewModelScope
) {

    private val _status = MutableStateFlow<String>("Ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _processedResults = MutableStateFlow<List<String>>(emptyList())
    val processedResults: StateFlow<List<String>> = _processedResults.asStateFlow()

    // You will need a CoroutineExceptionHandler for some parts
    private val generalExceptionHandler = CoroutineExceptionHandler { context, exception ->
        val job = context[Job]
        _status.update { "Critical Error (Job: $job): ${exception.localizedMessage ?: exception.toString()}" }
        println("GLOBAL HANDLER CAUGHT: $exception from Job: $job")
        // No re-throwing, let the handler do its job
    }

    fun processBatch(items: List<DataItem>) {
        scope.launch(generalExceptionHandler) { // Launch into the injected scope, using the generalExceptionHandler
            _status.update { "Processing batch of ${items.size} items..." }
            val results = mutableListOf<String>()
            supervisorScope {
                items.map {
                    async { processSingleItem(it) }
                }.forEach {
                    try {
                        val result = it.await()
                        if (result != null) {
                            results.add(result)
                        }
                    }
                    catch (e: Exception) {
                        if (e is CancellationException) { throw e }

                        println("BATCH ITEM ERROR (await caught): ${e.localizedMessage ?: e.toString()}")
                    }
                }
            }

            _processedResults.update { results.toList() }
            _status.update { "Batch processing completed. Processed ${results.size} items successfully." }
        }
    }

    private suspend fun processSingleItem(item: DataItem): String? {
        val isValid = try {
            ExternalService.validateData(item.payload)
        }
        catch (e: IllegalArgumentException) {
            println(e.localizedMessage)

            return null
        }

        val metadata = try {
            ExternalService.fetchMetaData(item.id)
        }
        catch (e: IOException) {
            println(e.localizedMessage)
            "DEFAULT_META"
        }

        if (isValid) {
            val result = item.payload + metadata
            try {
                ExternalService.storeResult(
                    id = item.id,
                    result = result
                )

                return result
            }
            catch (e: RuntimeException) {
                if (e is CancellationException) { throw e }

                println(e.localizedMessage)
                return null
            }
        }

        return null
    }

    fun clearResults() {
        _processedResults.update { emptyList() }
        _status.update { "Results cleared. Ready." }
    }
}