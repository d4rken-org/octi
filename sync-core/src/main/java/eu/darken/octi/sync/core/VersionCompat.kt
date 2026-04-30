package eu.darken.octi.sync.core

object VersionCompat {
    const val MIN_COMPATIBLE_VERSION = "0.14.0"

    fun isOutdated(version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        return compareVersions(version, MIN_COMPATIBLE_VERSION) < 0
    }

    fun isAtLeast(version: String?, minimum: String?): Boolean {
        if (version.isNullOrBlank() || minimum.isNullOrBlank()) return false
        return compareVersions(version, minimum) >= 0
    }

    /**
     * Compares two version strings using major.minor.patch components.
     * Strips any `-rc*`, `-beta*`, or other suffixes before comparing.
     * Returns negative if [a] < [b], zero if equal, positive if [a] > [b].
     */
    internal fun compareVersions(a: String, b: String): Int {
        val partsA = parseVersion(a)
        val partsB = parseVersion(b)
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val partA = partsA.getOrElse(i) { 0 }
            val partB = partsB.getOrElse(i) { 0 }
            if (partA != partB) return partA.compareTo(partB)
        }
        return 0
    }

    private fun parseVersion(version: String): List<Int> {
        val base = version.substringBefore("-")
        return base.split(".").mapNotNull { it.toIntOrNull() }
    }
}
