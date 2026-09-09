package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppMode {
    MERGE_PDF,
    IMAGE_TO_PDF,
    SPLIT_PDF,
    TOOLS
}

data class SelectedPdfItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeString: String
)

class MainActivity : ComponentActivity() {

    private var interstitialAd: InterstitialAd? = null
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        CoroutineScope(Dispatchers.Main).launch {
            delay(1200)
            keepSplashOnScreen = false
        }

        enableEdgeToEdge()

        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Exception) {}

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF121212),
                    bottomBar = {
                        AdBannerFooter()
                    }
                ) { innerPadding ->
                    PdfUnifierScreen(
                        modifier = Modifier.padding(innerPadding),
                        onShowInterstitial = { showInterstitialAd() },
                        onShowPasswordDialog = { callback ->
                            showPasswordDialog(callback)
                        }
                    )
                }
            }
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun showInterstitialAd() {
        interstitialAd?.let { ad ->
            ad.show(this)
            interstitialAd = null
            loadInterstitialAd()
        } ?: run {
            loadInterstitialAd()
        }
    }

    /**
     * Mostra la finestra di dialogo nativa Material 3 per l'inserimento della password di cifratura PDF
     */
    fun showPasswordDialog(onPasswordEntered: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password, null)
        val edtPassword = dialogView.findViewById<TextInputEditText>(R.id.edtPassword)
        val layoutPasswordInput = dialogView.findViewById<TextInputLayout>(R.id.layoutPasswordInput)

        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_PdfUnifier_Dialog)
            .setTitle(getString(R.string.lock_pdf_title))
            .setView(dialogView)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.lock_pdf_btn), null)
            .setNegativeButton(getString(R.string.info_close_button)) { d, _ -> d.dismiss() }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = edtPassword.text.toString().trim()
            if (password.isEmpty()) {
                layoutPasswordInput.error = getString(R.string.password_empty_error)
            } else {
                layoutPasswordInput.error = null
                onPasswordEntered(password)
                dialog.dismiss()
            }
        }
    }
}

@Composable
fun AdBannerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF191C17))
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("adView"),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUnifierScreen(
    modifier: Modifier = Modifier,
    onShowInterstitial: () -> Unit,
    onShowPasswordDialog: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val model: PdfViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = remember(context.applicationContext) {
            PdfViewModel.factory(context.applicationContext)
        }
    )
    var showHistoryBottomSheet by model.showHistoryBottomSheet
    val historyList by model.historyList
    var currentMode by model.currentMode
    val fileList = model.fileList
    var isCompressEnabled by model.isCompressEnabled
    val isProcessing by model.isProcessing
    var resultUri by model.resultUri
    val resultFileName by model.resultFileName
    var errorMessage by model.errorMessage
    var showInfoDialog by model.showInfoDialog
    var splitTotalPages by model.splitTotalPages
    var splitFromPageText by model.splitFromPageText
    var splitToPageText by model.splitToPageText
    var selectedRotationAngle by model.selectedRotationAngle
    val extractedTextResult by model.extractedTextResult
    var showExtractedTextSheet by model.showExtractedTextSheet
    fun refreshHistory() { model.refreshHistory() }
    fun inspectPdfPageCount(uri: Uri) { model.inspectPdfPageCount(uri) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { model.pendingShare.value }.filterNotNull().collect { uri ->
                model.pendingShare.value = null
                sharePdf(context, uri)
            }
        }
    }

    var pendingSaveSource by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSaveName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Document.pdf") }
    val saveCopyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val source = pendingSaveSource
        val name = pendingSaveName
        pendingSaveSource = null
        if (destination != null) {
            if (source != null) model.saveCopy(Uri.parse(source), destination, name)
            else errorMessage = context.getString(R.string.pdf_save_failed)
        }
    }

    // File pickers
    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> model.importFiles(uris, AppMode.MERGE_PDF, replace = false) }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> model.importFiles(uris, AppMode.IMAGE_TO_PDF, replace = false) }
    val pickSinglePdfForSplitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { model.importFiles(listOf(it), AppMode.SPLIT_PDF, replace = true) } }
    val pickSinglePdfForToolsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { model.importFiles(listOf(it), AppMode.TOOLS, replace = true) } }

    // Dialog Informazioni / Privacy
    if (showInfoDialog) {
        val privacyUrl = "https://pdfunifierpro.blogspot.com/p/privacy-policy.html"
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFB0BEC5),
            icon = {
                Icon(
                    imageVector = Icons.Default.PrivacyTip,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.info_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.info_message),
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(
                        text = stringResource(R.string.info_close_button),
                        color = Color(0xFF81C784),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.info_privacy_button),
                        color = Color(0xFF90CAF9),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    // Modal Bottom Sheet Testo Estratto
    if (showExtractedTextSheet && extractedTextResult != null) {
        ModalBottomSheet(
            onDismissRequest = { showExtractedTextSheet = false },
            containerColor = Color(0xFF1E1E1E),
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0D47A1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.extracted_text_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = {
                            copyToClipboard(context, extractedTextResult ?: "")
                        },
                        enabled = !extractedTextResult.isNullOrBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA5D6A7),
                            contentColor = Color(0xFF121212)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF121212)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.copy_text_btn),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF121212)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121212))
                        .padding(14.dp)
                ) {
                    val text = extractedTextResult ?: ""
                    if (text.isBlank()) {
                        Text(
                            text = stringResource(R.string.no_text_found),
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn {
                            item {
                                Text(
                                    text = text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Cronologia
    if (showHistoryBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistoryBottomSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E),
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1B5E20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                model.clearHistory()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.history_clear_all),
                                color = Color(0xFFE53935),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = stringResource(R.string.history_empty),
                                color = Color(0xFF888888),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(historyList, key = { _, item -> item.id }) { _, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val (typeIcon, typeBg, typeTint, typeLabelRes) = when (item.type) {
                                        "MERGE" -> arrayOf(Icons.Default.MergeType, Color(0xFF1B5E20), Color(0xFF81C784), R.string.history_type_merge)
                                        "IMAGE_TO_PDF" -> arrayOf(Icons.Default.Image, Color(0xFF0D47A1), Color(0xFF64B5F6), R.string.history_type_image_to_pdf)
                                        "SPLIT" -> arrayOf(Icons.Default.CallSplit, Color(0xFFE65100), Color(0xFFFFB74D), R.string.history_type_split)
                                        "LOCK" -> arrayOf(Icons.Default.Lock, Color(0xFFB71C1C), Color(0xFFE57373), R.string.history_type_lock)
                                        "ROTATE" -> arrayOf(Icons.Default.RotateRight, Color(0xFF4A148C), Color(0xFFBA68C8), R.string.history_type_rotate)
                                        "COMPRESS" -> arrayOf(Icons.Default.Description, Color(0xFF37474F), Color(0xFF90A4AE), R.string.pdf_compress_action)
                                        "EXPORT" -> arrayOf(Icons.Default.Save, Color(0xFF37474F), Color(0xFF90A4AE), R.string.pdf_save_as)
                                        else -> arrayOf(Icons.Default.Description, Color(0xFF37474F), Color(0xFF90A4AE), R.string.default_doc_name)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(typeBg as Color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = typeIcon as androidx.compose.ui.graphics.vector.ImageVector,
                                            contentDescription = null,
                                            tint = typeTint as Color,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.fileName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val formattedDate = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                            .format(Date(item.timestamp))
                                        Text(
                                            text = "${stringResource(typeLabelRes as Int)} • $formattedDate",
                                            color = Color(0xFFB0BEC5),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    // Pulsanti d'Azione (Apri, Estrai Testo, Condividi, Elimina)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { openPdfUri(context, item.fileUriString) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF252525))
                                                .testTag("btn_history_open_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = stringResource(R.string.history_open),
                                                tint = Color(0xFF81C784),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        // Estrai / Copia Testo direttamente dal file della Cronologia
                                        IconButton(
                                            onClick = {
                                                model.extractText(item.fileUriString)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF252525))
                                                .testTag("btn_history_extract_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.extract_text_btn),
                                                tint = Color(0xFFFFB74D),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { sharePdfUri(context, item.fileUriString) },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF252525))
                                                .testTag("btn_history_share_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = stringResource(R.string.history_share),
                                                tint = Color(0xFF2196F3),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                model.deleteHistory(item.id)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF252525))
                                                .testTag("btn_history_delete_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.history_delete),
                                                tint = Color(0xFFE53935),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val fromPage = splitFromPageText.toIntOrNull() ?: 1
    val toPage = splitToPageText.toIntOrNull() ?: 1
    val isSplitRangeValid = if (currentMode == AppMode.SPLIT_PDF) {
        fromPage in 1..toPage && (splitTotalPages == 0 || toPage <= splitTotalPages)
    } else true

    val isReady = when (currentMode) {
        AppMode.MERGE_PDF -> fileList.size >= 2
        AppMode.IMAGE_TO_PDF -> fileList.size >= 1
        AppMode.SPLIT_PDF -> fileList.size == 1 && isSplitRangeValid
        AppMode.TOOLS -> fileList.size == 1
    }
    val canExecute = isReady && !isProcessing

    val performAction: (Boolean) -> Unit = { shouldShare ->
        if (canExecute) model.performAction(shouldShare)
    }
    val executeLockPdf: (Boolean) -> Unit = { shouldShare ->
        val inputUri = fileList.singleOrNull()?.uri
        if (inputUri != null && !isProcessing) {
            onShowPasswordDialog { password -> model.lock(inputUri, password, shouldShare) }
        }
    }
    val executeRotatePdf: (Boolean) -> Unit = { shouldShare -> model.rotate(shouldShare) }
    val executeExtractText: () -> Unit = {
        fileList.singleOrNull()?.uri?.let { model.extractText(it.toString()) }
    }

    if (isProcessing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.processing_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.processing_message))
                }
            },
            confirmButton = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    } else if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.operation_error_title)) },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text(stringResource(R.string.info_close_button))
                }
            }
        )
    } else if (resultUri != null) {
        val completedUri = resultUri
        PdfResultDialog(
            name = resultFileName.orEmpty(),
            detail = model.resultDetail.value,
            warning = model.historyWarning.value,
            onOpen = { completedUri?.let { openPdfUri(context, it.toString()) } },
            onShare = { completedUri?.let { sharePdf(context, it) } },
            onSaveAs = {
                completedUri?.let {
                    pendingSaveSource = it.toString()
                    pendingSaveName = pdfCopyName(resultFileName.orEmpty())
                    saveCopyLauncher.launch(pendingSaveName)
                }
            },
            onClose = { resultUri = null }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // ========================= LAYOUT TABLET (2 COLONNE) =========================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Tablet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B5E20)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = stringResource(R.string.app_display_name),
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.app_display_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                refreshHistory()
                                showHistoryBottomSheet = true
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E))
                                .testTag("btn_history")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.history_btn_desc),
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E))
                                .testTag("btn_info")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.info_dialog_title),
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Selettore a 4 Schede
                ModeTabSelector(
                    currentMode = currentMode,
                    onModeSelected = { mode ->
                        if (currentMode != mode) {
                            currentMode = mode
                            fileList.clear()
                            resultUri = null
                            errorMessage = null
                            splitTotalPages = 0
                            splitFromPageText = "1"
                            splitToPageText = "1"
                        }
                    }
                )

                // Area 2 Colonne Affiancate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Colonna Sinistra (Controlli & Opzioni)
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Card Titolo & Subtitle
                        TitleInfoCard(currentMode = currentMode)

                        // Pulsante Aggiungi File
                        Button(
                            onClick = {
                                when (currentMode) {
                                    AppMode.MERGE_PDF -> pickPdfLauncher.launch(arrayOf("application/pdf"))
                                    AppMode.IMAGE_TO_PDF -> pickImageLauncher.launch(arrayOf("image/*"))
                                    AppMode.SPLIT_PDF -> pickSinglePdfForSplitLauncher.launch(arrayOf("application/pdf"))
                                    AppMode.TOOLS -> pickSinglePdfForToolsLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("btnAddFiles"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA5D6A7),
                                contentColor = Color(0xFF121212)
                            )
                        ) {
                            Icon(
                                imageVector = when (currentMode) {
                                    AppMode.MERGE_PDF -> Icons.Default.Add
                                    AppMode.IMAGE_TO_PDF -> Icons.Default.AddPhotoAlternate
                                    AppMode.SPLIT_PDF -> Icons.Default.FileOpen
                                    AppMode.TOOLS -> Icons.Default.Build
                                },
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color(0xFF121212)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (currentMode) {
                                    AppMode.MERGE_PDF -> stringResource(R.string.add_pdf_btn)
                                    AppMode.IMAGE_TO_PDF -> stringResource(R.string.add_images_btn)
                                    AppMode.SPLIT_PDF -> stringResource(R.string.select_pdf_btn)
                                    AppMode.TOOLS -> stringResource(R.string.select_tool_pdf_btn)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF121212)
                            )
                        }

                        // Opzioni Divisione
                        if (currentMode == AppMode.SPLIT_PDF) {
                            SplitOptionsCard(
                                splitTotalPages = splitTotalPages,
                                splitFromPageText = splitFromPageText,
                                splitToPageText = splitToPageText,
                                onFromChanged = { if (it.all { c -> c.isDigit() }) splitFromPageText = it },
                                onToChanged = { if (it.all { c -> c.isDigit() }) splitToPageText = it }
                            )
                        }

                        // Opzioni Compressione (Merge, Image, Split)
                        if (currentMode != AppMode.TOOLS) {
                            CompressOptionCard(
                                currentMode = currentMode,
                                isCompressEnabled = isCompressEnabled,
                                onCheckedChange = { isCompressEnabled = it }
                            )
                        }

                        // Strumenti Avanzati (Solo in modalità TOOLS)
                        if (currentMode == AppMode.TOOLS && fileList.isNotEmpty()) {
                            ToolsOptionsPanel(
                                selectedAngle = selectedRotationAngle,
                                onAngleSelected = { selectedRotationAngle = it },
                                onLockSave = { executeLockPdf(false) },
                                onLockShare = { executeLockPdf(true) },
                                onRotateSave = { executeRotatePdf(false) },
                                onRotateShare = { executeRotatePdf(true) },
                                onExtractText = { executeExtractText() },
                                onCompress = { model.compressSelected() }
                            )
                        }
                    }

                    // Colonna Destra (Lista File & Pulsanti Azione)
                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FileListHeader(
                            currentMode = currentMode,
                            fileCount = fileList.size,
                            onClear = {
                                fileList.clear()
                                resultUri = null
                                errorMessage = null
                                splitTotalPages = 0
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(12.dp)
                        ) {
                            if (fileList.isEmpty()) {
                                PlaceholderEmptyView(currentMode = currentMode)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(fileList, key = { _, item -> item.id }) { index, item ->
                                        FileItemCard(
                                            item = item,
                                            index = index,
                                            totalCount = fileList.size,
                                            isMergeMode = currentMode == AppMode.MERGE_PDF,
                                            onMoveUp = {
                                                if (index > 0) {
                                                    val temp = fileList[index]
                                                    fileList[index] = fileList[index - 1]
                                                    fileList[index - 1] = temp
                                                }
                                            },
                                            onMoveDown = {
                                                if (index < fileList.size - 1) {
                                                    val temp = fileList[index]
                                                    fileList[index] = fileList[index + 1]
                                                    fileList[index + 1] = temp
                                                }
                                            },
                                            onDelete = {
                                                fileList.removeAt(index)
                                                resultUri = null
                                                errorMessage = null
                                                if (fileList.isEmpty()) splitTotalPages = 0
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Pulsanti d'Azione Primari (per Merge, Images, Split)
                        if (currentMode != AppMode.TOOLS) {
                            ActionButtonsRow(
                                currentMode = currentMode,
                                canExecute = canExecute,
                                onSave = { performAction(false) },
                                onShare = { performAction(true) }
                            )
                        }
                    }
                }
            }
        } else {
            // ========================= LAYOUT PHONE (VERTICALE) =========================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Phone
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1B5E20)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = stringResource(R.string.app_display_name),
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.app_display_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.3).sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    refreshHistory()
                                    showHistoryBottomSheet = true
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1E1E))
                                    .testTag("btn_history")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = stringResource(R.string.history_btn_desc),
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showInfoDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1E1E))
                                    .testTag("btn_info")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.info_dialog_title),
                                    tint = Color(0xFF888888),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Selettore Modalità a 4 Schede
                item {
                    ModeTabSelector(
                        currentMode = currentMode,
                        onModeSelected = { mode ->
                            if (currentMode != mode) {
                                currentMode = mode
                                fileList.clear()
                                resultUri = null
                                errorMessage = null
                                splitTotalPages = 0
                                splitFromPageText = "1"
                                splitToPageText = "1"
                            }
                        }
                    )
                }

                // Titolo & Info Card
                item {
                    TitleInfoCard(currentMode = currentMode)
                }

                // Pulsante Aggiungi File
                item {
                    Button(
                        onClick = {
                            when (currentMode) {
                                AppMode.MERGE_PDF -> pickPdfLauncher.launch(arrayOf("application/pdf"))
                                AppMode.IMAGE_TO_PDF -> pickImageLauncher.launch(arrayOf("image/*"))
                                AppMode.SPLIT_PDF -> pickSinglePdfForSplitLauncher.launch(arrayOf("application/pdf"))
                                AppMode.TOOLS -> pickSinglePdfForToolsLauncher.launch(arrayOf("application/pdf"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("btnAddFiles"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA5D6A7),
                            contentColor = Color(0xFF121212)
                        )
                    ) {
                        Icon(
                            imageVector = when (currentMode) {
                                AppMode.MERGE_PDF -> Icons.Default.Add
                                AppMode.IMAGE_TO_PDF -> Icons.Default.AddPhotoAlternate
                                AppMode.SPLIT_PDF -> Icons.Default.FileOpen
                                AppMode.TOOLS -> Icons.Default.Build
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color(0xFF121212)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentMode) {
                                AppMode.MERGE_PDF -> stringResource(R.string.add_pdf_btn)
                                AppMode.IMAGE_TO_PDF -> stringResource(R.string.add_images_btn)
                                AppMode.SPLIT_PDF -> stringResource(R.string.select_pdf_btn)
                                AppMode.TOOLS -> stringResource(R.string.select_tool_pdf_btn)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF121212)
                        )
                    }
                }

                // Opzioni Divisione
                if (currentMode == AppMode.SPLIT_PDF) {
                    item {
                        SplitOptionsCard(
                            splitTotalPages = splitTotalPages,
                            splitFromPageText = splitFromPageText,
                            splitToPageText = splitToPageText,
                            onFromChanged = { if (it.all { c -> c.isDigit() }) splitFromPageText = it },
                            onToChanged = { if (it.all { c -> c.isDigit() }) splitToPageText = it }
                        )
                    }
                }

                // Opzioni Compressione (Merge, Images, Split)
                if (currentMode != AppMode.TOOLS) {
                    item {
                        CompressOptionCard(
                            currentMode = currentMode,
                            isCompressEnabled = isCompressEnabled,
                            onCheckedChange = { isCompressEnabled = it }
                        )
                    }
                }

                // Strumenti Avanzati (Solo in modalità TOOLS)
                if (currentMode == AppMode.TOOLS && fileList.isNotEmpty()) {
                    item {
                        ToolsOptionsPanel(
                            selectedAngle = selectedRotationAngle,
                            onAngleSelected = { selectedRotationAngle = it },
                            onLockSave = { executeLockPdf(false) },
                            onLockShare = { executeLockPdf(true) },
                            onRotateSave = { executeRotatePdf(false) },
                            onRotateShare = { executeRotatePdf(true) },
                            onExtractText = { executeExtractText() },
                            onCompress = { model.compressSelected() }
                        )
                    }
                }

                // Header Lista File
                item {
                    FileListHeader(
                        currentMode = currentMode,
                        fileCount = fileList.size,
                        onClear = {
                            fileList.clear()
                            resultUri = null
                            errorMessage = null
                            splitTotalPages = 0
                        }
                    )
                }

                // Lista File o Placeholder
                if (fileList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1E1E1E))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PlaceholderEmptyView(currentMode = currentMode)
                        }
                    }
                } else {
                    itemsIndexed(fileList, key = { _, item -> item.id }) { index, item ->
                        FileItemCard(
                            item = item,
                            index = index,
                            totalCount = fileList.size,
                            isMergeMode = currentMode == AppMode.MERGE_PDF,
                            onMoveUp = {
                                if (index > 0) {
                                    val temp = fileList[index]
                                    fileList[index] = fileList[index - 1]
                                    fileList[index - 1] = temp
                                }
                            },
                            onMoveDown = {
                                if (index < fileList.size - 1) {
                                    val temp = fileList[index]
                                    fileList[index] = fileList[index + 1]
                                    fileList[index + 1] = temp
                                }
                            },
                            onDelete = {
                                fileList.removeAt(index)
                                resultUri = null
                                errorMessage = null
                                if (fileList.isEmpty()) splitTotalPages = 0
                            }
                        )
                    }
                }

                // Pulsanti Azione (Merge, Images, Split)
                if (currentMode != AppMode.TOOLS) {
                    item {
                        ActionButtonsRow(
                            currentMode = currentMode,
                            canExecute = canExecute,
                            onSave = { performAction(false) },
                            onShare = { performAction(true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModeTabSelector(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val tabs = listOf(
            Triple(AppMode.MERGE_PDF, Icons.Default.MergeType, R.string.tab_merge_pdf),
            Triple(AppMode.IMAGE_TO_PDF, Icons.Default.Image, R.string.tab_image_to_pdf),
            Triple(AppMode.SPLIT_PDF, Icons.Default.CallSplit, R.string.tab_split_pdf),
            Triple(AppMode.TOOLS, Icons.Default.Build, R.string.tab_tools_pdf)
        )

        tabs.forEach { (mode, icon, labelRes) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF2C2C2C) else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .testTag("tab_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color(0xFF81C784) else Color(0xFF888888),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(labelRes),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else Color(0xFF888888),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TitleInfoCard(currentMode: AppMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (currentMode) {
                        AppMode.MERGE_PDF -> Icons.Default.Add
                        AppMode.IMAGE_TO_PDF -> Icons.Default.PhotoLibrary
                        AppMode.SPLIT_PDF -> Icons.Default.CallSplit
                        AppMode.TOOLS -> Icons.Default.Build
                    },
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = when (currentMode) {
                        AppMode.MERGE_PDF -> stringResource(R.string.title_merge_pdf)
                        AppMode.IMAGE_TO_PDF -> stringResource(R.string.title_image_to_pdf)
                        AppMode.SPLIT_PDF -> stringResource(R.string.title_split_pdf)
                        AppMode.TOOLS -> stringResource(R.string.title_tools_pdf)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = when (currentMode) {
                        AppMode.MERGE_PDF -> stringResource(R.string.subtitle_merge_pdf)
                        AppMode.IMAGE_TO_PDF -> stringResource(R.string.subtitle_image_to_pdf)
                        AppMode.SPLIT_PDF -> stringResource(R.string.subtitle_split_pdf)
                        AppMode.TOOLS -> stringResource(R.string.subtitle_tools_pdf)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SplitOptionsCard(
    splitTotalPages: Int,
    splitFromPageText: String,
    splitToPageText: String,
    onFromChanged: (String) -> Unit,
    onToChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.split_range_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = if (splitTotalPages > 0)
                    stringResource(R.string.split_total_pages, splitTotalPages)
                else
                    stringResource(R.string.split_load_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (splitTotalPages > 0) Color(0xFF81C784) else Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = splitFromPageText,
                    onValueChange = onFromChanged,
                    label = { Text(stringResource(R.string.split_from_label), color = Color(0xFFB0BEC5), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF81C784),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_split_from"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = splitToPageText,
                    onValueChange = onToChanged,
                    label = { Text(stringResource(R.string.split_to_label), color = Color(0xFFB0BEC5), fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF81C784),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF252525),
                        unfocusedContainerColor = Color(0xFF252525)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_split_to"),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
fun CompressOptionCard(
    currentMode: AppMode,
    isCompressEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCompressEnabled) Color(0xFF1B5E20) else Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    tint = if (isCompressEnabled) Color(0xFF81C784) else Color(0xFFB0BEC5),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (currentMode) {
                        AppMode.MERGE_PDF -> stringResource(R.string.compress_pdf_title)
                        AppMode.IMAGE_TO_PDF -> stringResource(R.string.compress_images_title)
                        AppMode.SPLIT_PDF -> stringResource(R.string.compress_pdf_title)
                        AppMode.TOOLS -> stringResource(R.string.compress_pdf_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        currentMode == AppMode.IMAGE_TO_PDF && isCompressEnabled ->
                            stringResource(R.string.pdf_image_compact)
                        currentMode == AppMode.IMAGE_TO_PDF ->
                            stringResource(R.string.pdf_image_high_quality)
                        isCompressEnabled -> stringResource(R.string.pdf_compress_checkbox)
                        else -> stringResource(R.string.compress_inactive_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCompressEnabled) Color(0xFF81C784) else Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = isCompressEnabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF81C784),
                    checkedTrackColor = Color(0xFF1B5E20),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF333333)
                ),
                modifier = Modifier.testTag("switch_compress")
            )
        }
    }
}

@Composable
fun ToolsOptionsPanel(
    selectedAngle: Int,
    onAngleSelected: (Int) -> Unit,
    onLockSave: () -> Unit,
    onLockShare: () -> Unit,
    onRotateSave: () -> Unit,
    onRotateShare: () -> Unit,
    onExtractText: () -> Unit,
    onCompress: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // PDF compression entry
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pdf_compress_action), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pdf_compress_explanation), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCompress, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pdf_compress_action))
                }
            }
        }

        // Card 1: Proteggi con Password (Lock PDF)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFB71C1C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.lock_pdf_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Cifratura AES-128 protetta con password",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onLockSave,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_lock_save"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373), contentColor = Color(0xFF121212))
                    ) {
                        Text(stringResource(R.string.lock_pdf_action), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onLockShare,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_lock_share"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373))
                    ) {
                        Text("Cifra & Condividi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Card 2: Ruota Pagine (Rotate PDF)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF4A148C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = null,
                            tint = Color(0xFFBA68C8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.rotate_pdf_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.rotate_pdf_subtitle),
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }
                }

                // Angoli Selezionabili
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val angles = listOf(
                        90 to "90°",
                        180 to "180°",
                        270 to "270°"
                    )
                    angles.forEach { (ang, label) ->
                        val isSelected = selectedAngle == ang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFBA68C8) else Color(0xFF2C2C2C))
                                .clickable { onAngleSelected(ang) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF121212) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRotateSave,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_rotate_save"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA68C8), contentColor = Color(0xFF121212))
                    ) {
                        Text(stringResource(R.string.rotate_save_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRotateShare,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("btn_rotate_share"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBA68C8))
                    ) {
                        Text(stringResource(R.string.rotate_share_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Card 3: Estrazione Testo (Text Stripper)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE65100)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.extract_text_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.extract_text_subtitle),
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onExtractText,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("btn_extract_text"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D), contentColor = Color(0xFF121212))
                ) {
                    Text(stringResource(R.string.extract_text_btn), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FileListHeader(
    currentMode: AppMode,
    fileCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val label = when (currentMode) {
            AppMode.MERGE_PDF -> stringResource(R.string.selected_documents_count, fileCount)
            AppMode.IMAGE_TO_PDF -> stringResource(R.string.selected_images_count, fileCount)
            AppMode.SPLIT_PDF -> stringResource(R.string.selected_split_file_count, fileCount)
            AppMode.TOOLS -> "File selezionato per strumenti ($fileCount):"
        }
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        if (fileCount > 0) {
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("btnClearList")
            ) {
                Text(
                    text = stringResource(R.string.clear_list_btn),
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PlaceholderEmptyView(currentMode: AppMode) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = when (currentMode) {
                AppMode.MERGE_PDF -> Icons.Default.PictureAsPdf
                AppMode.IMAGE_TO_PDF -> Icons.Default.AddPhotoAlternate
                AppMode.SPLIT_PDF -> Icons.Default.CallSplit
                AppMode.TOOLS -> Icons.Default.Build
            },
            contentDescription = null,
            tint = Color(0xFF444444),
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = when (currentMode) {
                AppMode.MERGE_PDF -> stringResource(R.string.no_files_merge_placeholder)
                AppMode.IMAGE_TO_PDF -> stringResource(R.string.no_files_images_placeholder)
                AppMode.SPLIT_PDF -> stringResource(R.string.no_files_split_placeholder)
                AppMode.TOOLS -> "Nessun PDF selezionato.\nCarica un file per cifrarlo, ruotarlo o estrarre il testo."
            },
            color = Color(0xFF888888),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun FileItemCard(
    item: SelectedPdfItem,
    index: Int,
    totalCount: Int,
    isMergeMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B5E20)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color(0xFF81C784),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.sizeString,
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp
                )
            }

            if (isMergeMode && totalCount > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = stringResource(R.string.move_up),
                        tint = if (index > 0) Color.White else Color(0xFF555555),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = stringResource(R.string.move_down),
                        tint = if (index < totalCount - 1) Color.White else Color(0xFF555555),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_file),
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ActionButtonsRow(
    currentMode: AppMode,
    canExecute: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val saveTextRes = when (currentMode) {
            AppMode.MERGE_PDF -> R.string.merge_save_btn
            AppMode.IMAGE_TO_PDF -> R.string.convert_save_btn
            AppMode.SPLIT_PDF -> R.string.split_save_btn
            AppMode.TOOLS -> R.string.merge_save_btn
        }
        val shareTextRes = when (currentMode) {
            AppMode.MERGE_PDF -> R.string.merge_share_btn
            AppMode.IMAGE_TO_PDF -> R.string.convert_share_btn
            AppMode.SPLIT_PDF -> R.string.split_share_btn
            AppMode.TOOLS -> R.string.merge_share_btn
        }

        Button(
            onClick = onSave,
            enabled = canExecute,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("btnMergeSave"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canExecute) Color(0xFFA5D6A7) else Color(0xFF2C2C2C),
                contentColor = if (canExecute) Color(0xFF121212) else Color(0xFF777777),
                disabledContainerColor = Color(0xFF222222),
                disabledContentColor = Color(0xFF555555)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(saveTextRes),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = onShare,
            enabled = canExecute,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("btnMergeShare"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canExecute) Color(0xFF81C784) else Color(0xFF2C2C2C),
                contentColor = if (canExecute) Color(0xFF121212) else Color(0xFF777777),
                disabledContainerColor = Color(0xFF222222),
                disabledContentColor = Color(0xFF555555)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(shareTextRes),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// ======================================================================================
// ==================== CORE PDF FUNCTIONS (OFFLINE & SECURE) ===========================
// ======================================================================================

/**
 * Cifra un PDF esistente impostando una password di accesso (Cifratura AES-128).
 * @param inputFile Il file PDF di origine non protetto.
 * @param outputFile Il file PDF di destinazione cifrato.
 * @param password La password scelta dall'utente.
 */
private fun lockPdf(inputFile: File, outputFile: File, password: String) {
    val document = PDDocument.load(inputFile)
    try {
        val accessPermission = AccessPermission()
        val protectionPolicy = StandardProtectionPolicy(password, password, accessPermission)
        protectionPolicy.encryptionKeyLength = 128 // Standard di sicurezza a 128-bit
        document.protect(protectionPolicy)
        document.save(outputFile)
    } finally {
        try { document.close() } catch (_: Exception) {}
    }
}

/**
 * Ruota tutte le pagine di un PDF di un determinato angolo.
 * @param inputFile Il file PDF di origine.
 * @param outputFile Il file PDF di destinazione ruotato.
 * @param angleDegrees Angolo di rotazione (es. 90, 180, 270).
 */
private fun rotatePdfPages(inputFile: File, outputFile: File, angleDegrees: Int) {
    val document = PDDocument.load(inputFile)
    try {
        for (page in document.pages) {
            val currentRotation = page.rotation
            page.rotation = (currentRotation + angleDegrees) % 360
        }
        document.save(outputFile)
    } finally {
        try { document.close() } catch (_: Exception) {}
    }
}

/**
 * Estrae interamente il testo leggibile contenuto all'interno di un file PDF.
 * @param inputFile Il file PDF da scansionare.
 * @return Il testo estratto sotto forma di stringa.
 */
private fun extractTextFromPdf(inputFile: File): String {
    val document = PDDocument.load(inputFile)
    return try {
        val stripper = PDFTextStripper()
        val text = stripper.getText(document)
        if (text.isNullOrBlank()) "" else text.trim()
    } catch (e: Exception) {
        e.printStackTrace()
        "Errore durante l'estrazione del testo: ${e.message}"
    } finally {
        try { document.close() } catch (_: Exception) {}
    }
}

internal suspend fun protectPdfWithPassword(
    context: Context,
    uri: Uri,
    password: String
): Pair<Uri, String> = withContext(Dispatchers.IO) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputFileName = "PDF_Protetto_$timeStamp.pdf"
    val tempRaw = File(context.cacheDir, "raw_$outputFileName")
    val tempEncrypted = File(context.cacheDir, outputFileName)

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempRaw.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Impossibile aprire il file PDF")

        lockPdf(tempRaw, tempEncrypted, password)

        Pair(publishPdf(context, tempEncrypted, outputFileName), outputFileName)
    } finally {
        if (tempRaw.exists()) tempRaw.delete()
        if (tempEncrypted.exists()) tempEncrypted.delete()
    }
}

internal suspend fun rotatePdf(
    context: Context,
    uri: Uri,
    angleDegrees: Int
): Pair<Uri, String> = withContext(Dispatchers.IO) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputFileName = "PDF_Ruotato_${angleDegrees}deg_$timeStamp.pdf"
    val tempRaw = File(context.cacheDir, "raw_$outputFileName")
    val tempRotated = File(context.cacheDir, outputFileName)

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempRaw.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Impossibile aprire il file PDF")

        rotatePdfPages(tempRaw, tempRotated, angleDegrees)

        Pair(publishPdf(context, tempRotated, outputFileName), outputFileName)
    } finally {
        if (tempRaw.exists()) tempRaw.delete()
        if (tempRotated.exists()) tempRotated.delete()
    }
}

internal suspend fun extractTextFromPdfUri(
    context: Context,
    uriOrPath: String
): String = withContext(Dispatchers.IO) {
    val tempRaw = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}.pdf")
    try {
        if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            val uri = Uri.parse(uriOrPath)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempRaw.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext "Impossibile leggere il file"
        } else {
            val file = File(uriOrPath)
            if (!file.exists()) return@withContext "File non trovato"
            file.copyTo(tempRaw, overwrite = true)
        }
        extractTextFromPdf(tempRaw)
    } catch (e: Exception) {
        "Errore durante l'estrazione del testo: ${e.message}"
    } finally {
        if (tempRaw.exists()) tempRaw.delete()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("PDF Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.text_copied_toast), Toast.LENGTH_SHORT).show()
}

internal suspend fun getPdfPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val document = PDDocument.load(inputStream)
        try {
            document.numberOfPages
        } finally {
            try { document.close() } catch (_: Exception) {}
        }
    } ?: 0
}

internal suspend fun mergeMultiplePdfs(
    context: Context,
    uris: List<Uri>,
    compress: Boolean
): Pair<Uri, String> = withContext(Dispatchers.IO) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputFileName = "PDF_Unito_$timeStamp.pdf"
    val tempFile = File(context.cacheDir, outputFileName)

    val merger = PDFMergerUtility()
    merger.destinationFileName = tempFile.absolutePath

    val inputStreams = mutableListOf<InputStream>()
    try {
        for (uri in uris) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Unable to open stream for URI: $uri")
            inputStreams.add(inputStream)
            merger.addSource(inputStream)
        }

        merger.mergeDocuments(null)

        if (compress) {
            val uncompressedTemp = File(context.cacheDir, "raw_$outputFileName")
            try {
                tempFile.copyTo(uncompressedTemp, overwrite = true)
                compressPdfOffline(uncompressedTemp, tempFile)
            } finally {
                if (uncompressedTemp.exists()) uncompressedTemp.delete()
            }
        }

        Pair(publishPdf(context, tempFile, outputFileName), outputFileName)
    } finally {
        for (stream in inputStreams) {
            try { stream.close() } catch (_: Exception) {}
        }
        if (tempFile.exists()) tempFile.delete()
    }
}

internal suspend fun convertImagesToPdf(
    context: Context,
    uris: List<Uri>,
    compress: Boolean
): Pair<Uri, String> = withContext(Dispatchers.IO) {
    require(uris.isNotEmpty() && uris.size <= 100) { context.getString(R.string.pdf_input_limit) }
    val outputFileName = "Immagini_${System.currentTimeMillis()}.pdf"
    val tempFile = File.createTempFile("images_pdf_", ".pdf", context.cacheDir)
    try {
        val memory = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
            .setTempDir(context.cacheDir)
        PDDocument(memory).use { document ->
            for ((index, uri) in uris.withIndex()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val bitmap = try {
                    decodePdfImage(context, uri, if (compress) 2200 else 3200)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    throw java.io.IOException(context.getString(R.string.pdf_image_item_failed,
                        index + 1, failure.localizedMessage.orEmpty()), failure)
                }
                try {
                    val page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    val image = if (compress && !bitmap.hasAlpha()) {
                        JPEGFactory.createFromImage(document, bitmap, 0.80f)
                    } else {
                        LosslessFactory.createFromImage(document, bitmap)
                    }
                    val scale = minOf(PDRectangle.A4.width / bitmap.width,
                        PDRectangle.A4.height / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    PDPageContentStream(document, page).use {
                        it.drawImage(image, (PDRectangle.A4.width - width) / 2,
                            (PDRectangle.A4.height - height) / 2, width, height)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            document.save(tempFile)
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        Pair(publishPdf(context, tempFile, outputFileName), outputFileName)
    } finally {
        tempFile.delete()
    }
}

internal suspend fun splitPdf(
    context: Context,
    uri: Uri,
    fromPage: Int,
    toPage: Int,
    compress: Boolean
): Pair<Uri, String> = withContext(Dispatchers.IO) {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val outputFileName = "PDF_Estratto_p${fromPage}_p${toPage}_$timeStamp.pdf"
    val tempFile = File.createTempFile("split_", ".pdf", context.cacheDir)
    try {
        (context.contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Unable to open PDF stream")).use { input ->
            PDDocument.load(input).use { source ->
                require(fromPage >= 1 && toPage >= fromPage && toPage <= source.numberOfPages) {
                    "Invalid page range: $fromPage-$toPage / ${source.numberOfPages}"
                }
                PDDocument().use { output ->
                    for (index in (fromPage - 1) until toPage) {
                        val page = source.getPage(index)
                        val imported = output.importPage(page)
                        // Resources can be inherited from the source page tree.
                        imported.resources = page.resources
                    }
                    output.save(tempFile)
                }
            }
        }
        if (compress) {
            val rawFile = File.createTempFile("split_raw_", ".pdf", context.cacheDir)
            try {
                tempFile.copyTo(rawFile, overwrite = true)
                compressPdfOffline(rawFile, tempFile)
            } finally {
                rawFile.delete()
            }
        }
        Pair(publishPdf(context, tempFile, outputFileName), outputFileName)
    } finally {
        tempFile.delete()
    }
}

private fun sharePdf(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.merge_share_btn)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_share_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
    }
}

private fun openPdfUri(context: Context, uriOrPath: String, name: String = "PDF") {
    openPdfReader(context, uriOrPath, name)
}

private fun sharePdfUri(context: Context, uriOrPath: String) {
    try {
        val uri = if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
            Uri.parse(uriOrPath)
        } else {
            val file = File(uriOrPath)
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.history_file_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.history_share)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_share_error, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
    }
}

private fun getFileInfo(context: Context, uri: Uri): Pair<String, String> {
    var name = context.getString(R.string.default_doc_name)
    var size = context.getString(R.string.unknown_size)

    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: name
                }
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    val sizeBytes = it.getLong(sizeIndex)
                    size = formatFileSize(sizeBytes)
                }
            }
        }
    } catch (_: Exception) {}

    return Pair(name, size)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes B"
    }
}
