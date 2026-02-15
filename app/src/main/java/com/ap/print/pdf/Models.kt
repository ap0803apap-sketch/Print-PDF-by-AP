package com.ap.print.pdf

import android.graphics.Bitmap

enum class PageSelectionType { ALL, ODD, EVEN, CUSTOM }
enum class ScaleType { FIT, CUSTOM }
enum class Orientation { PORTRAIT, LANDSCAPE }
enum class PrintingMode { SINGLE_SIDED, DOUBLE_SIDED }
enum class MarginType { PRESET, CUSTOM }

data class PdfSettings(
    val pageSelectionType: PageSelectionType = PageSelectionType.ALL,
    val customPageString: String = "",
    val pagesPerSheet: Int = 1,
    val scaleType: ScaleType = ScaleType.FIT,
    val customScalePercent: Int = 100,
    val pageSizeName: String = "A4",
    val orientation: Orientation = Orientation.PORTRAIT,
    val showBorder: Boolean = false,
    // NEW: Double-sided printing (DEFAULT: SINGLE_SIDED)
    val printingMode: PrintingMode = PrintingMode.SINGLE_SIDED,
    // NEW: Margin settings
    val marginType: MarginType = MarginType.PRESET,
    val marginPreset: String = "Fit to Paper",
    val customMarginMm: Float = 0f
)

data class ProcessingState(
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val previewBitmap: Bitmap? = null
)