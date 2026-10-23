package com.nida.kstudio

data class BuildDiagnostic(val path: String, val line: Int, val message: String) {
    companion object {
        // Kotlin 2, Kotlin 1, javac and AAPT use different location formats.
        private val location = Regex("(?:file://)?([^\\s()]+\\.(?:kt|kts|java|xml))(?::(\\d+)(?::\\d+)?|: \\((\\d+), \\d+\\))[: ]*(.*)")

        fun parse(log: String): List<BuildDiagnostic> = log.lineSequence().mapNotNull { raw ->
            val match = location.find(raw) ?: return@mapNotNull null
            val absolute = match.groupValues[1]
            val path = when {
                "/kstudio-work/" in absolute -> absolute.substringAfter("/kstudio-work/")
                else -> absolute.removePrefix("./")
            }
            BuildDiagnostic(
                path,
                (match.groupValues[2].ifEmpty { match.groupValues[3] }).toIntOrNull() ?: 1,
                match.groupValues[4].ifBlank { raw }
            )
        }.distinct().toList()
    }
}
