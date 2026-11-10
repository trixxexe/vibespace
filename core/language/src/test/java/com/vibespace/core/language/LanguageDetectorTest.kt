package com.vibespace.core.language

import com.vibespace.core.language.detector.DefaultLanguageDetector
import com.vibespace.core.language.model.LanguageDefinition
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    private val detector = DefaultLanguageDetector()

    @Test
    fun detects_kotlin_by_extension_and_name() {
        assertEquals(LanguageDefinition.Kotlin, detector.detectByExtension("kt"))
        assertEquals(LanguageDefinition.Kotlin, detector.detectByFileName("Main.kt"))
        assertEquals(LanguageDefinition.Kotlin, detector.detectByFileName("build.gradle.kts"))
    }

    @Test
    fun detects_python_and_shebang() {
        assertEquals(LanguageDefinition.Python, detector.detectByExtension("py"))
        assertEquals(LanguageDefinition.Python, detector.detectByContent("#!/usr/bin/env python3\nprint('hi')"))
    }

    @Test
    fun detects_javascript_and_rust() {
        assertEquals(LanguageDefinition.JavaScript, detector.detectByExtension("js"))
        assertEquals(LanguageDefinition.Rust, detector.detectByExtension("rs"))
    }

    @Test
    fun falls_back_to_plain_text_for_unknown() {
        assertEquals(LanguageDefinition.PlainText, detector.detectByExtension("xyz123"))
    }
}
