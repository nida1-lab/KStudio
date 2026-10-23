package com.nida.kstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildDiagnosticTest {
    @Test fun kotlinCompilerErrorMapsToProjectFile() {
        val result = BuildDiagnostic.parse("e: file:///home/runner/work/KStudio/KStudio/kstudio-work/MainActivity.kt:18:7 Unresolved reference 'missing'.")
        assertEquals("MainActivity.kt", result.single().path)
        assertEquals(18, result.single().line)
        assertTrue(result.single().message.contains("Unresolved reference"))
    }
    @Test fun javacAndAaptLocationsAreSupported() {
        val result = BuildDiagnostic.parse("/tmp/kstudio-work/app/src/main/java/Main.java:9: error: cannot find symbol\n/tmp/kstudio-work/app/src/main/res/layout/main.xml:12: error: resource missing")
        assertEquals(listOf(9, 12), result.map { it.line })
        assertEquals("app/src/main/res/layout/main.xml", result.last().path)
    }
    @Test fun oldKotlinLocationAndDuplicateMessages() {
        val log = "e: /tmp/kstudio-work/Helper.kt: (3, 5): Syntax error"
        assertEquals(1, BuildDiagnostic.parse("$log\n$log").size)
        assertEquals(3, BuildDiagnostic.parse(log).single().line)
    }
    @Test fun taskMessagesAreNotErrors() {
        assertTrue(BuildDiagnostic.parse("> Task :app:compileDebugKotlin\nBUILD SUCCESSFUL").isEmpty())
    }
}
