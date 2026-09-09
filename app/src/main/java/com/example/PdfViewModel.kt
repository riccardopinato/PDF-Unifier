package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PdfViewModel(
    context: Context,
    private val engine: PdfEngine = AndroidPdfEngine(context),
    private val history: PdfHistory = LocalPdfHistory(context),
    private val inputReader: PdfInputReader = AndroidPdfInputReader(context)
) : ViewModel() {
    private val app = context.applicationContext
    val currentMode = mutableStateOf(AppMode.MERGE_PDF)
    val fileList = mutableStateListOf<SelectedPdfItem>()
    val isCompressEnabled = mutableStateOf(false)
    val isProcessing = mutableStateOf(false)
    val resultUri = mutableStateOf<Uri?>(null)
    val resultFileName = mutableStateOf<String?>(null)
    val errorMessage = mutableStateOf<String?>(null)
    val showInfoDialog = mutableStateOf(false)
    val splitTotalPages = mutableIntStateOf(0)
    val splitFromPageText = mutableStateOf("1")
    val splitToPageText = mutableStateOf("1")
    val selectedRotationAngle = mutableIntStateOf(90)
    val extractedTextResult = mutableStateOf<String?>(null)
    val showExtractedTextSheet = mutableStateOf(false)
    val showHistoryBottomSheet = mutableStateOf(false)
    val historyList = mutableStateOf<List<HistoryItem>>(emptyList())
    val historyWarning = mutableStateOf<String?>(null)
    val resultDetail = mutableStateOf<String?>(null)
    val pendingShare = mutableStateOf<Uri?>(null)
    private var inspection: Job? = null
    private var inspectionVersion = 0
    private var historyVersion = 0

    fun importFiles(uris: List<Uri>, mode: AppMode, replace: Boolean) {
        if (uris.isEmpty() || isProcessing.value) return
        val inputs = uris.distinct().toList()
        if (inputs.size > 100 || (!replace && fileList.size + inputs.count { uri -> fileList.none { it.uri == uri } } > 100)) {
            errorMessage.value = app.getString(R.string.pdf_input_limit)
            return
        }
        if (!startOperation()) return
        viewModelScope.launch {
            try {
                val imported = inputReader.read(inputs)
                // Commit only after every input was read successfully.
                if (replace) fileList.clear()
                currentMode.value = mode
                fileList.addAll(imported.filter { item -> fileList.none { it.uri == item.uri } })
                splitTotalPages.intValue = 0
                splitFromPageText.value = "1"
                splitToPageText.value = "1"
                if (mode == AppMode.SPLIT_PDF || mode == AppMode.TOOLS) {
                    fileList.singleOrNull()?.uri?.let { inspectPdfPageCount(it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = describeError(failure)
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun inspectPdfPageCount(uri: Uri) {
        val version = ++inspectionVersion
        inspection?.cancel()
        splitTotalPages.intValue = 0
        inspection = viewModelScope.launch {
            try {
                val count = engine.pageCount(uri)
                if (version == inspectionVersion && fileList.singleOrNull()?.uri == uri) {
                    splitTotalPages.intValue = count
                    splitFromPageText.value = "1"
                    splitToPageText.value = count.coerceAtLeast(1).toString()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version == inspectionVersion && fileList.singleOrNull()?.uri == uri) {
                    errorMessage.value = describeError(failure)
                }
            }
        }
    }

    fun refreshHistory() = updateHistory { }
    fun deleteHistory(id: Int) = updateHistory { history.delete(id) }
    fun clearHistory() = updateHistory { history.clear() }

    private fun updateHistory(change: suspend () -> Unit) {
        val version = ++historyVersion
        viewModelScope.launch {
            try {
                change()
                val items = history.load()
                if (version == historyVersion) historyList.value = items
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (version == historyVersion) errorMessage.value = describeError(failure)
            }
        }
    }

    fun performAction(share: Boolean) {
        val action = when (currentMode.value) {
            AppMode.MERGE_PDF -> PdfAction.MERGE
            AppMode.IMAGE_TO_PDF -> PdfAction.IMAGE_TO_PDF
            AppMode.SPLIT_PDF -> PdfAction.SPLIT
            AppMode.TOOLS -> return
        }
        generate(PdfRequest(
            action, fileList.map { it.uri }, isCompressEnabled.value,
            splitFromPageText.value.toIntOrNull() ?: 0,
            splitToPageText.value.toIntOrNull() ?: 0
        ), share)
    }

    fun lock(uri: Uri, password: String, share: Boolean) =
        generate(PdfRequest(PdfAction.LOCK, listOf(uri), password = password), share)

    fun rotate(share: Boolean) {
        val uri = fileList.singleOrNull()?.uri ?: return
        generate(PdfRequest(PdfAction.ROTATE, listOf(uri), angle = selectedRotationAngle.intValue), share)
    }

    fun compressSelected() {
        val uri = fileList.singleOrNull()?.uri ?: return
        if (!startOperation()) return
        viewModelScope.launch {
            try {
                val result = compressExistingPdf(app, uri)
                resultUri.value = result.uri
                resultFileName.value = result.name
                resultDetail.value = result.report.summary(app)
                recordResult(result.uri, result.name, "COMPRESS")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = describeError(failure)
            } finally {
                isProcessing.value = false
            }
        }
    }

    private suspend fun recordResult(uri: Uri, name: String, type: String) {
        try {
            val version = ++historyVersion
            history.add(name, uri.toString(), type)
            val items = history.load()
            if (version == historyVersion) historyList.value = items
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            historyWarning.value = app.getString(R.string.pdf_history_warning)
        }
    }

    fun saveCopy(source: Uri, destination: Uri, name: String) {
        if (isProcessing.value) return
        historyVersion++
        inspectionVersion++
        inspection?.cancel()
        isProcessing.value = true
        errorMessage.value = null
        historyWarning.value = null
        pendingShare.value = null
        viewModelScope.launch {
            try {
                val actualName = savePdfCopy(app, source, destination, name)
                resultUri.value = destination
                resultFileName.value = actualName
                resultDetail.value = app.getString(R.string.pdf_save_success)
                recordResult(destination, actualName, "EXPORT")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = app.getString(R.string.pdf_save_failed)
            } finally {
                isProcessing.value = false
            }
        }
    }

    private fun startOperation(): Boolean {
        if (isProcessing.value) return false
        historyVersion++
        inspectionVersion++
        inspection?.cancel()
        isProcessing.value = true
        resultUri.value = null
        resultFileName.value = null
        errorMessage.value = null
        historyWarning.value = null
        resultDetail.value = null
        pendingShare.value = null
        showHistoryBottomSheet.value = false
        showExtractedTextSheet.value = false
        return true
    }

    private fun generate(request: PdfRequest, share: Boolean) {
        if (!startOperation()) return
        viewModelScope.launch {
            try {
                val result = engine.generate(request)
                resultUri.value = result.first
                resultFileName.value = result.second
                recordResult(result.first, result.second, request.action.name)
                if (share) pendingShare.value = result.first
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = describeError(failure)
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun extractText(uriOrPath: String) {
        if (!startOperation()) return
        viewModelScope.launch {
            try {
                extractedTextResult.value = engine.extractText(uriOrPath)
                showExtractedTextSheet.value = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                errorMessage.value = describeError(failure)
            } finally {
                isProcessing.value = false
            }
        }
    }

    private fun describeError(failure: Exception): String = when (failure) {
        is InvalidPasswordException -> app.getString(R.string.pdf_password_required)
        is SecurityException -> app.getString(R.string.pdf_access_denied)
        else -> failure.localizedMessage ?: app.getString(R.string.operation_error_title)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PdfViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return PdfViewModel(app) as T
                }
            }
        }
    }
}
