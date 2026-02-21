package com.ap.print.pdf

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import android.widget.Toast
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfProcessingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationManager: NotificationManager? = null
    private var cachedImageBitmaps: List<Bitmap> = emptyList()
    private val notificationId = 1

    var onProgress: ((Float, String) -> Unit)? = null
    var onPreviewReady: ((Bitmap) -> Unit)? = null
    var onComplete: ((Uri?) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): PdfProcessingService = this@PdfProcessingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification(0, "Initializing Service..."))
    }

    // ================= LOAD PDF =================

    fun loadPdf(uri: Uri, contentResolver: android.content.ContentResolver) {
        serviceScope.launch {
            try {
                cachedImageBitmaps = emptyList()
                updateNotification(0, "Importing PDF...")
                onProgress?.invoke(0.1f, "Opening file...")

                val inputStream = contentResolver.openInputStream(uri)
                val cacheFile = File(cacheDir, "temp_import.pdf")
                val outputStream = FileOutputStream(cacheFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                onProgress?.invoke(0.5f, "Generating Preview...")

                val fd =
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val page = renderer.openPage(0)

                val bitmap =
                    Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                onPreviewReady?.invoke(bitmap)

                page.close()
                renderer.close()
                fd.close()

                updateNotification(100, "Import Complete")

            } catch (e: Exception) {
                onProgress?.invoke(0f, "Error: ${e.message}")
            }
        }
    }

    fun processImages(uris: List<Uri>, context: Context, settings: PdfSettings) {
        serviceScope.launch(Dispatchers.Default) {
            try {
                updateNotification(0, "Loading images...")

                onProgress?.invoke(0.1f, "Loading images...")

                val bitmaps = mutableListOf<Bitmap>()
                for ((index, uri) in uris.withIndex()) {
                    val progress = 0.2f + (index.toFloat() / uris.size) * 0.6f
                    onProgress?.invoke(progress, "Loading image ${index + 1}/${uris.size}...")

                    val bitmap = loadBitmapFromUri(uri, context)
                    if (bitmap != null) {
                        bitmaps.add(bitmap)
                    }
                }

                if (bitmaps.isNotEmpty()) {
                    onProgress?.invoke(0.85f, "Preparing preview...")
                    // Show first image as preview
                    onPreviewReady?.invoke(bitmaps[0])

                    // Cache bitmaps for printing
                    cachedImageBitmaps = bitmaps
                }

                onProgress?.invoke(1.0f, "Ready to print (${bitmaps.size} images)")
            } catch (e: Exception) {
                onProgress?.invoke(1.0f, "Error: ${e.message}")
            }
        }
    }

    // ================= SAVE PDF =================

    fun savePdf(context: Context, settings: PdfSettings, originalFileName: String) {
        serviceScope.launch {
            if (cachedImageBitmaps.isNotEmpty()) {
                saveImagesAsPdf(context, settings, originalFileName)
            } else {
                savePdfFromPdf(context, settings, originalFileName)
            }
        }
    }

    private suspend fun saveImagesAsPdf(context: Context, settings: PdfSettings, originalFileName: String) {
        try {
            updateNotification(0, "Processing Images into PDF...")
            onProgress?.invoke(0f, "Starting render...")

            val imagesToProcess = cachedImageBitmaps

            val finalImagesList = if (settings.printingMode == PrintingMode.DOUBLE_SIDED) {
                val result = mutableListOf<Bitmap?>()
                for (image in imagesToProcess) {
                    result.add(image)
                    result.add(null)
                }
                result.dropLast(1)
            } else {
                imagesToProcess.map { it as Bitmap? }
            }

            val outputDoc = PdfDocument()
            val (outW, outH) = PageSizeUtils.getDimensions(settings.pageSizeName, settings.orientation)

            val spacingBetweenPages = if (settings.marginType == MarginType.PRESET) {
                MarginPresets.getMarginInPoints(settings.marginPreset)
            } else {
                MarginPresets.convertMmToPoints(settings.customMarginMm)
            }

            val (cols, rows) = when (settings.pagesPerSheet) {
                1 -> 1 to 1
                2 -> if (settings.orientation == Orientation.LANDSCAPE) 2 to 1 else 1 to 2
                4 -> 2 to 2
                6 -> if (settings.orientation == Orientation.LANDSCAPE) 3 to 2 else 2 to 3
                9 -> 3 to 3
                16 -> 4 to 4
                else -> 1 to 1
            }

            val totalSpacingWidth = spacingBetweenPages * (cols - 1)
            val totalSpacingHeight = spacingBetweenPages * (rows - 1)
            val availableW = outW - totalSpacingWidth
            val availableH = outH - totalSpacingHeight
            val cellW = availableW / cols
            val cellH = availableH / rows

            var itemsOnPage = 0
            var currentPageInfo: PdfDocument.PageInfo? = null
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null

            for ((index, bitmap) in finalImagesList.withIndex()) {
                updateNotification(index * 100 / finalImagesList.size, "Rendering image ${index + 1}/${finalImagesList.size}")
                onProgress?.invoke(index.toFloat() / finalImagesList.size, "Rendering...")

                if (itemsOnPage == 0) {
                    currentPageInfo = PdfDocument.PageInfo.Builder(outW.toInt(), outH.toInt(), index + 1).create()
                    currentPage = outputDoc.startPage(currentPageInfo)
                    canvas = currentPage.canvas
                    canvas.drawColor(Color.WHITE)
                }

                if (bitmap != null) {
                    val col = itemsOnPage % cols
                    val row = itemsOnPage / cols
                    val cellX = col * (cellW + spacingBetweenPages)
                    val cellY = row * (cellH + spacingBetweenPages)
                    val scale = if (settings.scaleType == ScaleType.CUSTOM) settings.customScalePercent / 100f else 1.0f

                    val aspectSrc = bitmap.width.toFloat() / bitmap.height
                    val aspectDst = cellW / cellH
                    var drawW = cellW
                    var drawH = cellH
                    if (aspectSrc > aspectDst) {
                        drawH = drawW / aspectSrc
                    } else {
                        drawW = drawH * aspectSrc
                    }

                    drawW *= scale
                    drawH *= scale
                    val offX = cellX + (cellW - drawW) / 2
                    val offY = cellY + (cellH - drawH) / 2
                    val destRect = RectF(offX, offY, offX + drawW, offY + drawH)
                    val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                    canvas!!.drawBitmap(bitmap, null, destRect, paint)

                    if (settings.showBorder) {
                        val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
                        canvas.drawRect(destRect, borderPaint)
                    }
                }
                itemsOnPage++

                if (itemsOnPage >= settings.pagesPerSheet) {
                    outputDoc.finishPage(currentPage)
                    itemsOnPage = 0
                }
            }

            if (itemsOnPage > 0) {
                outputDoc.finishPage(currentPage)
            }

            onProgress?.invoke(0.9f, "Saving file...")
            val timeStamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val finalName = "${originalFileName}_PrintPDF_$timeStamp.pdf"
            val outputUri = saveToDownloads(context, outputDoc, finalName)
            outputDoc.close()

            stopForeground(STOP_FOREGROUND_REMOVE)
            showSavedNotification(finalName, outputUri)
            onProgress?.invoke(1.0f, "Done!")
            onComplete?.invoke(outputUri)
            stopSelf()
        } catch (e: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            e.printStackTrace()
        }
    }

    private suspend fun savePdfFromPdf(context: Context, settings: PdfSettings, originalFileName: String) {
        try {
            updateNotification(0, "Processing PDF...")
            onProgress?.invoke(0f, "Starting render...")

            val inputFile = File(cacheDir, "temp_import.pdf")
            if (!inputFile.exists()) {
                onProgress?.invoke(1f, "Error: No source file found.")
                return
            }

            val inputFd =
                ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(inputFd)
            val totalInputPages = renderer.pageCount

            val pagesToProcess = when (settings.pageSelectionType) {
                PageSelectionType.ALL -> (0 until totalInputPages).toList()
                PageSelectionType.ODD -> (0 until totalInputPages).filter { (it + 1) % 2 != 0 }
                PageSelectionType.EVEN -> (0 until totalInputPages).filter { (it + 1) % 2 == 0 }
                PageSelectionType.CUSTOM -> parseCustomPages(settings.customPageString, totalInputPages)
            }

            val finalPagesList = if (settings.printingMode == PrintingMode.DOUBLE_SIDED) {
                val result = mutableListOf<Int?>()
                for (pageIndex in pagesToProcess) {
                    result.add(pageIndex)
                    result.add(null)
                }
                result.dropLast(1)
            } else {
                pagesToProcess.map { it as Int? }
            }

            val outputDoc = PdfDocument()
            val (outW, outH) =
                PageSizeUtils.getDimensions(settings.pageSizeName, settings.orientation)

            val spacingBetweenPages = if (settings.marginType == MarginType.PRESET) {
                MarginPresets.getMarginInPoints(settings.marginPreset)
            } else {
                MarginPresets.convertMmToPoints(settings.customMarginMm)
            }

            val (cols, rows) = when (settings.pagesPerSheet) {
                1 -> 1 to 1
                2 -> if (settings.orientation == Orientation.LANDSCAPE) 2 to 1 else 1 to 2
                4 -> 2 to 2
                6 -> if (settings.orientation == Orientation.LANDSCAPE) 3 to 2 else 2 to 3
                9 -> 3 to 3
                16 -> 4 to 4
                else -> 1 to 1
            }

            val totalSpacingWidth = spacingBetweenPages * (cols - 1)
            val totalSpacingHeight = spacingBetweenPages * (rows - 1)
            val availableW = outW - totalSpacingWidth
            val availableH = outH - totalSpacingHeight
            val cellW = availableW / cols
            val cellH = availableH / rows

            var itemsOnPage = 0
            var currentPageInfo: PdfDocument.PageInfo? = null
            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null

            for ((index, pageIndex) in finalPagesList.withIndex()) {
                val progress = (index.toFloat() / finalPagesList.size * 100).toInt()
                updateNotification(progress, "Rendering ${index + 1}/${finalPagesList.size}")
                onProgress?.invoke(progress / 100f, "Rendering...")

                if (itemsOnPage == 0) {
                    currentPageInfo =
                        PdfDocument.PageInfo.Builder(outW.toInt(), outH.toInt(), index + 1)
                            .create()
                    currentPage = outputDoc.startPage(currentPageInfo)
                    canvas = currentPage!!.canvas
                    canvas.drawColor(Color.WHITE)
                }

                if (pageIndex != null) {
                    val inpPage = renderer.openPage(pageIndex)
                    val bitmapW = inpPage.width * 2
                    val bitmapH = inpPage.height * 2
                    val bitmap =
                        Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                    inpPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    inpPage.close()

                    val col = itemsOnPage % cols
                    val row = itemsOnPage / cols
                    val cellX = col * (cellW + spacingBetweenPages)
                    val cellY = row * (cellH + spacingBetweenPages)

                    var scale = 1.0f
                    if (settings.scaleType == ScaleType.CUSTOM) {
                        scale = settings.customScalePercent / 100f
                    }

                    val aspectSrc = bitmap.width.toFloat() / bitmap.height
                    val aspectDst = cellW / cellH

                    var drawW = cellW
                    var drawH = cellH

                    if (aspectSrc > aspectDst) {
                        drawH = drawW / aspectSrc
                    } else {
                        drawW = drawH * aspectSrc
                    }

                    drawW *= scale
                    drawH *= scale

                    val offX = cellX + (cellW - drawW) / 2
                    val offY = cellY + (cellH - drawH) / 2

                    val destRect = RectF(offX, offY, offX + drawW, offY + drawH)

                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    canvas!!.drawBitmap(bitmap, null, destRect, paint)

                    if (settings.showBorder) {
                        val borderPaint = Paint().apply {
                            color = Color.LTGRAY
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                        }
                        canvas!!.drawRect(destRect, borderPaint)
                    }

                    bitmap.recycle()
                }

                itemsOnPage++

                if (itemsOnPage >= settings.pagesPerSheet) {
                    outputDoc.finishPage(currentPage)
                    itemsOnPage = 0
                }
            }

            if (itemsOnPage > 0) {
                outputDoc.finishPage(currentPage)
            }

            renderer.close()
            inputFd.close()

            onProgress?.invoke(0.9f, "Saving file...")

            val timeStamp =
                SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            val finalName = "${originalFileName}_PrintPDF_$timeStamp.pdf"

            val outputUri = saveToDownloads(context, outputDoc, finalName)
            outputDoc.close()

            stopForeground(STOP_FOREGROUND_REMOVE)

            showSavedNotification(finalName, outputUri)

            onProgress?.invoke(1.0f, "Done!")
            onComplete?.invoke(outputUri)

            stopSelf()

        } catch (e: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            e.printStackTrace()
        }
    }

    private fun loadBitmapFromUri(uri: Uri, context: Context): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    // ================= UTILITIES =================

    private fun parseCustomPages(input: String, maxPages: Int): List<Int> {
        val result = mutableSetOf<Int>()

        input.split(",").forEach { part ->
            val trimmed = part.trim()

            if (trimmed.contains("-")) {
                val range = trimmed.split("-")
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()

                    if (start != null && end != null) {
                        for (i in start..end) {
                            if (i in 1..maxPages) {
                                result.add(i - 1)
                            }
                        }
                    }
                }
            } else {
                val page = trimmed.toIntOrNull()
                if (page != null && page in 1..maxPages) {
                    result.add(page - 1)
                }
            }
        }

        return result.sorted()
    }

    // ================= FILE SAVE =================

    private suspend fun saveToDownloads(context: Context, doc: PdfDocument, filename: String): Uri? {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Print PDF By AP"
            )
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Saved: ${Environment.DIRECTORY_DOWNLOADS + "/Print PDF by AP/$filename"}",
                Toast.LENGTH_LONG
            ).show()
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                doc.writeTo(out)
            }
        }

        return uri
    }

    // ================= NOTIFICATIONS =================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pdf_service",
                "PDF Processing",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int, text: String): Notification {
        return NotificationCompat.Builder(this, "pdf_service")
            .setContentTitle("Print PDF by AP")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(progress: Int, text: String) {
        notificationManager?.notify(notificationId, createNotification(progress, text))
    }

    private fun showSavedNotification(fileName: String, uri: Uri?) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notification = NotificationCompat.Builder(this, "pdf_service")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("PDF Saved Successfully")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager?.notify(2, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}