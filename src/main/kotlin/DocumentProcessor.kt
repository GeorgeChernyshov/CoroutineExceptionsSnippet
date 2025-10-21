package org.example

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

data class Document(
    val id: String,
    val rawContent: String? = null,
    val parsedData: String? = null,
    val enrichedData: Map<String, String> = emptyMap()
)

data class ProcessedDocument(
    val document: Document,
    val status: String,
    val errorMessage: String? = null
)

class ParsingException(message: String) : Exception(message)
class DocumentEnrichingException(message: String) : Exception(message)
class DocumentValidationException(message: String) : Exception(message)

// --- Simulated Services ---
object DocumentService {
    suspend fun fetchDocumentContent(id: String, attempt: Int = 1): String {
        println("  -> Fetching content for $id (Attempt $attempt)")
        delay(150 + attempt * 50L) // Simulate longer delay on retries
        if (id == "doc001" && attempt < 2) {
            println("  <- FAILED fetch for $id (Attempt $attempt)")
            throw IOException("Network error fetching doc001 (attempt $attempt)") // Fails once
        }
        if (id == "doc002") {
            println("  <- FAILED fetch for $id (Permanent error)")
            throw IOException("Permanent network error fetching doc002") // Always fails
        }
        println("  <- SUCCESS fetch for $id (Attempt $attempt)")
        return "Content for $id"
    }

    suspend fun parseDocument(content: String): String {
        println("  -> Parsing content: ${content.take(20)}...")
        delay(100)
        if (content.contains("error_parse")) {
            println("  <- FAILED parse for content: ${content.take(20)}...")
            throw ParsingException("Failed to parse content: $content")
        }
        println("  <- SUCCESS parse for content: ${content.take(20)}...")
        return "Parsed: $content"
    }

    suspend fun fetchKeywords(parsedData: String): String {
        println("    -> Fetching keywords for parsed data: ${parsedData.take(20)}...")
        delay(150)
        if (parsedData.contains("critical_keyword_error")) {
            println("    <- FAILED keywords for parsed data: ${parsedData.take(20)}...")
            throw RuntimeException("Critical keyword API error")
        }
        println("    <- SUCCESS keywords for parsed data: ${parsedData.take(20)}...")
        return "Keywords: ${parsedData.substring(0, minOf(parsedData.length, 10))}"
    }

    suspend fun fetchRelatedDocuments(parsedData: String): String {
        println("    -> Fetching related docs for parsed data: ${parsedData.take(20)}...")
        delay(300) // Longer but optional
        if (parsedData.contains("related_doc_timeout")) {
            println("    -> SIMULATING related docs timeout for parsed data: ${parsedData.take(20)}...")
            delay(5000) // Simulate timeout
        }
        if (parsedData.contains("optional_api_error")) {
            println("    <- FAILED related docs for parsed data: ${parsedData.take(20)}...")
            throw Exception("Optional related docs API error")
        }
        println("    <- SUCCESS related docs for parsed data: ${parsedData.take(20)}...")
        return "Related Docs: ${parsedData.substring(0, minOf(parsedData.length, 10))}"
    }

    suspend fun validateDocument(document: Document): Boolean {
        println("  -> Validating document: ${document.id}")
        delay(50)
        if (document.parsedData == null || document.enrichedData.isEmpty()) {
            println("  <- FAILED validation for ${document.id}: Incomplete")
            throw DocumentValidationException("Incomplete document for validation")
        }
        if (document.id == "doc005") {
            println("  <- FAILED validation for ${document.id}: Logic failure")
            throw DocumentValidationException("Validation logic failed for doc005")
        }
        println("  <- SUCCESS validation for ${document.id}")
        return true
    }

    suspend fun archiveDocument(document: Document): String {
        println("  -> Archiving document: ${document.id}")
        delay(100)
        if (document.id == "doc006") {
            println("  <- FAILED archiving for ${document.id}")
            throw RuntimeException("Archiving failed for doc006")
        }
        println("  <- SUCCESS archiving for ${document.id}")
        return "Archived: ${document.id}"
    }
}

class DocumentProcessor(
    private val scope: CoroutineScope // e.g., viewModelScope
) {
    private val _processorStatus = MutableStateFlow("Idle")
    val processorStatus: StateFlow<String> = _processorStatus.asStateFlow()

    private val _documentStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val documentStatuses: StateFlow<Map<String, String>> = _documentStatuses.asStateFlow()

    private val _processedDocuments = MutableStateFlow<List<ProcessedDocument>>(emptyList())
    val processedDocuments = _processedDocuments.map { docs ->
        docs.map { "${it.document.id}: ${it.status} ${it.errorMessage ?: ""}" }
    }

    private var processingJob: Job? = null
    private val globalExceptionHandler = CoroutineExceptionHandler { context, exception ->
        val job = context[Job]
        println("GLOBAL HANDLER CAUGHT: $exception from Job: $job")
        // No re-throwing, let the handler do its job
    }

    fun startProcessingBatch(documentIdsToProcess: List<String>) {
        processingJob = Job()
        processingJob?.also {
            scope.launch(it + globalExceptionHandler) {
                println("Processing batch of ${documentIdsToProcess.size} documents...")
                _documentStatuses.value = documentIdsToProcess.associateWith { "Pending" }
                _processorStatus.value = "Processing"

                val deferreds = documentIdsToProcess.map {
                    async { processSingleDocument(it) }
                }

                val processedDocs = deferreds.mapIndexed { index, task ->
                    try {
                        task.await()
                    }
                    catch (e: Exception) {
                        if (e is CancellationException) throw e

                        ProcessedDocument(
                            document = Document(documentIdsToProcess[index]),
                            status = "Failed",
                            errorMessage = e.localizedMessage
                        )
                    }
                }

                _processedDocuments.value = processedDocs
                _processorStatus.value = "Completed"
            }
        }
    }

    fun cancelAllProcessing() {
        processingJob?.cancel()

        _processorStatus.value = "Cancelled"
        // Optionally, update the statuses of any still-pending/in-progress documents
        _documentStatuses.update { currentMap ->
            currentMap.mapValues { (docId, status) ->
                // Only update if it's an active status, not already failed or processed
                if (status != "Processed" && !status.startsWith("Failed")) {
                    "Cancelled" // Mark as cancelled
                } else {
                    status // Keep existing status if it's already failed or processed
                }
            }
        }
    }

    private suspend fun processSingleDocument(documentId: String) : ProcessedDocument {
        _documentStatuses.update { it + (documentId to "Fetching") }

        var currentDocument = Document(documentId)

        val content = try {
            tryFetchingDocument(documentId) ?: throw IOException()
        }
        catch (e: IOException) {
            val status = "Failed: Fetching"
            _documentStatuses.update { it + (documentId to status) }

            return ProcessedDocument(
                document = currentDocument,
                status = status,
                errorMessage = e.localizedMessage
            )
        }

        currentDocument = currentDocument.copy(rawContent = content)

        _documentStatuses.update { it + (documentId to "Parsing") }
        val parsedData = try {
             DocumentService.parseDocument(content)
        }
        catch (e: ParsingException) {
            val status = "Failed: Parsing"
            _documentStatuses.update { it + (documentId to status) }

            return ProcessedDocument(
                document = currentDocument,
                status = status,
                errorMessage = e.localizedMessage
            )
        }

        currentDocument = currentDocument.copy(parsedData = parsedData)

        _documentStatuses.update { it + (documentId to "Enriching") }
        try {
            var relatedDocuments: String? = null
            var keywords: String? = null

            supervisorScope {
                val relatedDocumentsJob = async {
                    withTimeout(500) {
                        try {
                            DocumentService.fetchRelatedDocuments(parsedData)
                        }
                        catch (e: TimeoutCancellationException) {
                            null
                        }
                        catch (e: Exception) {
                            if (e is CancellationException) throw e

                            println("Fetch related documents caught an Exception: ${e.localizedMessage}")
                            null
                        }
                    }
                }

                val keywordsJob = async {
                    DocumentService.fetchKeywords(parsedData)
                }

                try {
                    relatedDocuments = relatedDocumentsJob.await()
                    keywords = keywordsJob.await()
                }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw DocumentEnrichingException(e.localizedMessage)
                }
            }

            val enrichedData: MutableMap<String, String> = HashMap()
            relatedDocuments?.let { enrichedData["Related"] = it }
            keywords?.let { enrichedData["Keywords"] = it }

            currentDocument = currentDocument.copy(enrichedData = enrichedData)

            _documentStatuses.update { it + (documentId to "Validating") }
            val isValid = try {
                DocumentService.validateDocument(currentDocument)
            }
            catch (e: DocumentValidationException) {
                val status = "Failed: Validating"
                _documentStatuses.update { it + (documentId to status) }

                return ProcessedDocument(
                    document = currentDocument,
                    status = status,
                    errorMessage = e.localizedMessage
                )
            }

            if (isValid) {
                _documentStatuses.update { it + (documentId to "Archiving") }

                try {
                    DocumentService.archiveDocument(currentDocument)
                }
                catch (e: Exception) {
                    if (e is CancellationException) throw e

                    val status = "Failed: Archiving"
                    _documentStatuses.update { it + (documentId to status) }

                    return ProcessedDocument(
                        document = currentDocument,
                        status = status,
                        errorMessage = e.localizedMessage
                    )
                }
            }

            _documentStatuses.update { it + (documentId to "Processed") }

            return ProcessedDocument(
                document = currentDocument,
                status = "Processed",
                errorMessage = null
            )
        }
        catch (e: CancellationException) {
            throw e
        }
        catch (e: DocumentEnrichingException) {
            val status = "Failed: Enriching"
            _documentStatuses.update { it + (documentId to status) }

            return ProcessedDocument(
                document = currentDocument,
                status = status,
                errorMessage = e.localizedMessage
            )
        }
        catch (e: Exception) {
            val status = "Failed"
            _documentStatuses.update { it + (documentId to status) }

            return ProcessedDocument(
                document = currentDocument,
                status = status,
                errorMessage = e.localizedMessage
            )
        }
    }

    private suspend fun tryFetchingDocument(documentId: String) : String? {
        var content: String? = null
        var attemptCount = 1

        while (attemptCount <= 3) {
            try {
                content = DocumentService.fetchDocumentContent(documentId, attemptCount)
                break
            }
            catch (e: IOException) {
                if (attemptCount == 3)
                    throw e

                delay(50)
                attemptCount++
            }
        }

        return content
    }
}