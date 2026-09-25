package com.example.data

import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.regex.Pattern

object DocumentTextExtractor {

    fun extractTextFromDocx(inputStream: InputStream): String {
        val sb = StringBuilder()
        try {
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val bytes = zipStream.readBytes()
                    val xmlContent = String(bytes, Charsets.UTF_8)
                    val text = cleanDocxXml(xmlContent)
                    sb.append(text)
                    break
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString().trim()
    }

    private fun cleanDocxXml(xml: String): String {
        val matcher = Pattern.compile("<w:p[ >](.*?)</w:p>").matcher(xml)
        val sb = StringBuilder()
        while (matcher.find()) {
            val paragraphXml = matcher.group(1) ?: ""
            val textMatcher = Pattern.compile("<w:t[^>]*>(.*?)</w:t>").matcher(paragraphXml)
            val pBuilder = StringBuilder()
            while (textMatcher.find()) {
                val piece = textMatcher.group(1) ?: ""
                val unescaped = piece
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                pBuilder.append(unescaped)
            }
            val paragraphText = pBuilder.toString().trim()
            if (paragraphText.isNotEmpty()) {
                sb.append(paragraphText).append("\n")
            }
        }
        return sb.toString().trim()
    }

    fun extractTextFromPdf(bytes: ByteArray): String {
        return PdfTextExtractor.extractText(bytes)
    }

    fun extractTextFromStream(inputStream: InputStream, mimeType: String, fileName: String): String {
        return try {
            val isDocx = mimeType.contains("word", ignoreCase = true) || 
                         mimeType.contains("docx", ignoreCase = true) || 
                         fileName.endsWith(".docx", ignoreCase = true)
            val isPdf = mimeType.contains("pdf", ignoreCase = true) || 
                        fileName.endsWith(".pdf", ignoreCase = true)

            if (isDocx) {
                extractTextFromDocx(inputStream)
            } else if (isPdf) {
                val bytes = inputStream.readBytes()
                extractTextFromPdf(bytes)
            } else {
                val bytes = inputStream.readBytes()
                String(bytes, Charsets.UTF_8).trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
