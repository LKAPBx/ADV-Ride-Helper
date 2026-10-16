package dev.africatwin.telemetry

/** Conservative decoder for dark seven-segment digits in an isolated TOTAL-mile region. */
internal object SegmentOdometer {
    fun read(width: Int, height: Int, gray: IntArray): Double? {
        if (width < 8 || height < 8 || gray.size != width * height) return null
        val sorted = gray.sorted()
        val low = sorted[sorted.size / 10]; val high = sorted[sorted.size * 9 / 10]
        if (high - low < 35) return null
        val threshold = (low + high) / 2
        fun ink(x: Int, y: Int) = gray[y * width + x] < threshold
        val rows = (0 until height).map { y -> (0 until width).count { x -> ink(x, y) } }
        val bands = mutableListOf<IntRange>()
        var start = -1
        for (y in 0..height) {
            val valid = y < height && rows[y] in 2..(width / 2)
            if (valid && start < 0) start = y
            if (!valid && start >= 0) { if (y - start >= 8) bands.add(start until y); start = -1 }
        }
        val band = bands.singleOrNull() ?: return null
        val columns = (0 until width).map { x -> band.count { y -> ink(x, y) } >= 2 }
        val groups = mutableListOf<IntRange>(); start = -1
        for (x in 0..width) {
            if (x < width && columns[x] && start < 0) start = x
            if ((x == width || !columns[x]) && start >= 0) { groups.add(start until x); start = -1 }
        }
        if (groups.size !in 2..7) return null
        val digits = StringBuilder()
        val patterns = mapOf(119 to '0', 36 to '1', 93 to '2', 109 to '3', 46 to '4', 107 to '5', 123 to '6', 37 to '7', 127 to '8', 111 to '9')
        for (group in groups) {
            val w = group.count(); val h = band.count()
            if (w < 2 || w > h * 1.2) return null
            val fill = band.sumOf { y -> group.count { x -> ink(x, y) } }.toDouble() / (w * h)
            if (fill > 0.7 || fill < 0.12) return null
            if (w.toDouble() / h < 0.38) { digits.append('1'); continue }
            fun segment(x0: Double, x1: Double, y0: Double, y1: Double): Boolean {
                val xs = (group.first + (w * x0).toInt())..(group.first + (w * x1).toInt()).coerceAtMost(group.last)
                val ys = (band.first + (h * y0).toInt())..(band.first + (h * y1).toInt()).coerceAtMost(band.last)
                val count = ys.sumOf { y -> xs.count { x -> ink(x, y) } }
                return count.toDouble() / (xs.count() * ys.count()) > 0.28
            }
            val regions = listOf(doubleArrayOf(.25,.75,0.0,.16), doubleArrayOf(0.0,.30,.20,.42), doubleArrayOf(.70,1.0,.20,.42),
                doubleArrayOf(.25,.75,.43,.58), doubleArrayOf(0.0,.30,.63,.84), doubleArrayOf(.70,1.0,.63,.84), doubleArrayOf(.25,.75,.86,1.0))
            var mask = 0
            regions.forEachIndexed { i, r -> if (segment(r[0], r[1], r[2], r[3])) mask = mask or (1 shl i) }
            digits.append(patterns[mask] ?: return null)
        }
        return digits.toString().toDoubleOrNull()?.takeIf { it <= 1000000 }
    }
}
