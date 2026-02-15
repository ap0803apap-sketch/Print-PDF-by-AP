package com.ap.print.pdf

// Margin presets in mm (converted to points: 1mm = 2.834645669 points)
object MarginPresets {
    const val MM_TO_POINTS = 2.834645669f

    // Standard margin presets
    const val NORMAL_MARGIN_MM = 19f       // 19mm (0.75 inch)
    const val NARROW_MARGIN_MM = 12.7f     // 12.7mm (0.5 inch)
    const val WIDE_MARGIN_MM = 38.1f       // 38.1mm (1.5 inch)
    const val FIT_TO_PRINTABLE_MM = 15.9f  // 15.9mm (0.625 inch)
    const val FIT_TO_PAPER_MM = 0f         // No margin

    fun getMarginInPoints(preset: String): Float {
        return when (preset) {
            "Normal" -> NORMAL_MARGIN_MM * MM_TO_POINTS
            "Narrow" -> NARROW_MARGIN_MM * MM_TO_POINTS
            "Wide" -> WIDE_MARGIN_MM * MM_TO_POINTS
            "Fit to Printable Area" -> FIT_TO_PRINTABLE_MM * MM_TO_POINTS
            "Fit to Paper" -> FIT_TO_PAPER_MM * MM_TO_POINTS
            else -> NORMAL_MARGIN_MM * MM_TO_POINTS
        }
    }

    fun convertMmToPoints(mm: Float): Float = mm * MM_TO_POINTS
}

object PageSizeUtils {

    fun getDimensions(size: String, orientation: Orientation): Pair<Float, Float> {

        // points @ 72dpi
        val (w, h) = when (size) {
            // ISO A Series
            "A0" -> 2384f to 3370f
            "A1" -> 1684f to 2384f
            "A2" -> 1191f to 1684f
            "A3" -> 842f to 1191f
            "A4" -> 595f to 842f
            "A5" -> 420f to 595f
            "A6" -> 298f to 420f
            "A7" -> 210f to 298f
            "A8" -> 147f to 210f
            "A9" -> 105f to 147f
            "A10" -> 73f to 105f

            // ISO B Series
            "B0" -> 2835f to 4008f
            "B1" -> 2004f to 2835f
            "B2" -> 1417f to 2004f
            "B3" -> 1001f to 1417f
            "B4" -> 709f to 1001f
            "B5" -> 501f to 709f
            "B6" -> 354f to 501f
            "B7" -> 250f to 354f
            "B8" -> 176f to 250f
            "B9" -> 125f to 176f
            "B10" -> 88f to 125f

            // ISO C Series (Envelopes)
            "C4" -> 649f to 918f
            "C5" -> 459f to 649f
            "C6" -> 323f to 459f

            // North American Sizes
            "Letter" -> 612f to 792f
            "Legal" -> 612f to 1008f
            "Tabloid" -> 792f to 1224f
            "Ledger" -> 1224f to 792f
            "Junior Legal" -> 504f to 792f
            "Half Letter" -> 396f to 612f

            // Photo Sizes
            "Photo 4x6" -> 288f to 432f
            "Photo 5x7" -> 360f to 504f
            "Photo 8x10" -> 576f to 720f
            "Photo 3x5" -> 216f to 360f

            // Business Sizes
            "Business Card" -> 252f to 144f
            "Postcard" -> 283f to 425f
            "Large Postcard" -> 425f to 567f

            // Other Popular Sizes
            "Envelope #10" -> 297f to 684f
            "Envelope #6" -> 252f to 432f
            "DL Envelope" -> 312f to 624f
            "Crown Octavo" -> 522f to 693f
            "Large Crown Octavo" -> 546f to 738f
            "Demy Octavo" -> 561f to 738f
            "Medium" -> 561f to 873f
            "Royal" -> 612f to 792f
            "Executive" -> 522f to 756f

            else -> 595f to 842f // A4 default
        }

        return if (orientation == Orientation.LANDSCAPE) h to w else w to h
    }

    fun getAvailableSizes(): List<String> = listOf(
        // ISO A Series
        "A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10",
        // ISO B Series
        "B0", "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9", "B10",
        // ISO C Series
        "C4", "C5", "C6",
        // North American
        "Letter", "Legal", "Tabloid", "Ledger", "Junior Legal", "Half Letter",
        // Photo Sizes
        "Photo 4x6", "Photo 5x7", "Photo 8x10", "Photo 3x5",
        // Business
        "Business Card", "Postcard", "Large Postcard",
        // Envelopes
        "Envelope #10", "Envelope #6", "DL Envelope",
        // Other
        "Crown Octavo", "Large Crown Octavo", "Demy Octavo", "Medium", "Royal", "Executive"
    )
}