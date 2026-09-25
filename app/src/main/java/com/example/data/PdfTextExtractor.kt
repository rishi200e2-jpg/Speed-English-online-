package com.example.data

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

object PdfTextExtractor {

    fun countPages(pdfBytes: ByteArray): Int {
        var count = 0
        var index = 0
        val seq1 = "/Type /Page".toByteArray(Charsets.US_ASCII)
        val seq2 = "/Type/Page".toByteArray(Charsets.US_ASCII)
        
        while (index < pdfBytes.size) {
            val i1 = findSequence(pdfBytes, seq1, index)
            val i2 = findSequence(pdfBytes, seq2, index)
            val foundIndex = if (i1 != -1 && i2 != -1) {
                minOf(i1, i2)
            } else if (i1 != -1) {
                i1
            } else if (i2 != -1) {
                i2
            } else {
                -1
            }
            
            if (foundIndex == -1) break
            
            count++
            index = foundIndex + 10
        }
        
        // If count is still 0, look for /Count in the /Type /Pages catalog
        if (count == 0) {
            val pagesSeq = "/Type /Pages".toByteArray(Charsets.US_ASCII)
            val pagesSeq2 = "/Type/Pages".toByteArray(Charsets.US_ASCII)
            var pIndex = 0
            while (pIndex < pdfBytes.size) {
                val pi1 = findSequence(pdfBytes, pagesSeq, pIndex)
                val pi2 = findSequence(pdfBytes, pagesSeq2, pIndex)
                val foundPagesIndex = if (pi1 != -1 && pi2 != -1) {
                    minOf(pi1, pi2)
                } else if (pi1 != -1) {
                    pi1
                } else if (pi2 != -1) {
                    pi2
                } else {
                    -1
                }
                if (foundPagesIndex == -1) break
                
                val windowSize = minOf(100, pdfBytes.size - foundPagesIndex)
                val windowStr = String(pdfBytes.copyOfRange(foundPagesIndex, foundPagesIndex + windowSize), Charsets.US_ASCII)
                val countRegex = Regex("/Count\\s+(\\d+)")
                val match = countRegex.find(windowStr)
                if (match != null) {
                    val countVal = match.groupValues[1].toIntOrNull()
                    if (countVal != null && countVal > 0) {
                        return countVal
                    }
                }
                pIndex = foundPagesIndex + 12
            }
        }
        
        return if (count == 0) 1 else count
    }

    fun extractText(pdfBytes: ByteArray): String {
        val result = StringBuilder()
        var index = 0
        while (index < pdfBytes.size) {
            // Find "stream"
            val streamStartIndex = findSequence(pdfBytes, "stream".toByteArray(), index)
            if (streamStartIndex == -1) break
            
            // Look backward from streamStartIndex to find the object header (to check filter)
            val objStartIndex = maxOf(0, streamStartIndex - 500)
            val headerString = String(pdfBytes.copyOfRange(objStartIndex, streamStartIndex), Charsets.US_ASCII)
            val isFlateFiltered = headerString.contains("/FlateDecode")
            
            // Find "endstream"
            val endStreamIndex = findSequence(pdfBytes, "endstream".toByteArray(), streamStartIndex)
            if (endStreamIndex == -1) break
            
            // Extract the stream content bytes
            // Skip the "stream" keyword and following newline (usually \r\n or \n)
            var contentStart = streamStartIndex + 6
            if (contentStart < pdfBytes.size && pdfBytes[contentStart] == '\r'.toByte()) contentStart++
            if (contentStart < pdfBytes.size && pdfBytes[contentStart] == '\n'.toByte()) contentStart++
            
            val contentEnd = endStreamIndex
            if (contentStart < contentEnd) {
                val streamBytes = pdfBytes.copyOfRange(contentStart, contentEnd)
                try {
                    val decompressedBytes = if (isFlateFiltered) {
                        decompressFlate(streamBytes)
                    } else {
                        streamBytes
                    }
                    val streamText = String(decompressedBytes, Charsets.UTF_8)
                    val extracted = parseTextFromStream(streamText)
                    if (extracted.isNotEmpty()) {
                        result.append(extracted).append("\n")
                    }
                } catch (e: Exception) {
                    // Ignore corrupted streams
                }
            }
            index = endStreamIndex + 9
        }
        
        var finalExtracted = cleanExtractedText(result.toString())
        if (finalExtracted.length >= 30) {
            return finalExtracted
        }

        // Fallback 1: Extract strings within parentheses ( ... )
        val parenSb = StringBuilder()
        var pIdx = 0
        while (pIdx < pdfBytes.size) {
            if (pdfBytes[pIdx] == '('.toByte()) {
                pIdx++
                val start = pIdx
                var depth = 1
                while (pIdx < pdfBytes.size && depth > 0) {
                    if (pdfBytes[pIdx] == '('.toByte()) depth++
                    else if (pdfBytes[pIdx] == ')'.toByte()) depth--
                    if (depth > 0) pIdx++
                }
                if (start < pIdx && pIdx < pdfBytes.size) {
                    val str = String(pdfBytes.copyOfRange(start, pIdx), Charsets.US_ASCII)
                    if (str.any { it.isLetterOrDigit() }) {
                        parenSb.append(str).append(" ")
                    }
                }
            }
            pIdx++
        }
        val parenText = cleanExtractedText(parenSb.toString())
        if (parenText.length >= 30) {
            return parenText
        }

        // Fallback 2: Extract printable ASCII character sequences
        val asciiSb = StringBuilder()
        var currentChunk = StringBuilder()
        for (b in pdfBytes) {
            val ch = b.toInt().toChar()
            if (ch in ' '..'~' || ch == '\n' || ch == '\r' || ch == '\t') {
                currentChunk.append(ch)
            } else {
                if (currentChunk.length >= 4) {
                    asciiSb.append(currentChunk.toString()).append(" ")
                }
                currentChunk.clear()
            }
        }
        if (currentChunk.length >= 4) asciiSb.append(currentChunk.toString())

        val filteredWords = asciiSb.toString()
            .split(Regex("\\s+"))
            .filter { word -> word.length in 2..35 && word.any { it.isLetter() } }
            .joinToString(" ")

        val printableResult = cleanExtractedText(filteredWords)
        return if (printableResult.isNotEmpty()) printableResult else finalExtracted
    }

    private fun decompressFlate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val outputStream = ByteArrayOutputStream(bytes.size)
        val buffer = ByteArray(1024)
        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                outputStream.write(buffer, 0, count)
            }
        } finally {
            inflater.end()
        }
        return outputStream.toByteArray()
    }

    private fun findSequence(data: ByteArray, seq: ByteArray, startIndex: Int): Int {
        if (seq.isEmpty()) return -1
        for (i in startIndex..data.size - seq.size) {
            var match = true
            for (j in seq.indices) {
                if (data[i + j] != seq[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun parseTextFromStream(streamText: String): String {
        val out = StringBuilder()
        var i = 0
        val len = streamText.length
        while (i < len) {
            val c = streamText[i]
            if (c == '(') {
                i++
                val start = i
                var escape = false
                while (i < len) {
                    val current = streamText[i]
                    if (escape) {
                        escape = false
                        i++
                        continue
                    }
                    if (current == '\\') {
                        escape = true
                        i++
                        continue
                    }
                    if (current == ')') {
                        break
                    }
                    i++
                }
                if (i < len && start < i) {
                    val rawText = streamText.substring(start, i)
                    val cleaned = decodePdfString(rawText)
                    out.append(cleaned)
                }
            } else if (c == '<' && i + 1 < len && streamText[i + 1] != '<') {
                // Hex string support: <48656c6c6f>
                i++
                val start = i
                while (i < len && streamText[i] != '>') {
                    i++
                }
                if (i < len && start < i) {
                    val hexStr = streamText.substring(start, i).filter { it.isLetterOrDigit() }
                    try {
                        val decoded = hexStr.chunked(2)
                            .map { it.toInt(16).toChar() }
                            .joinToString("")
                        out.append(decoded)
                    } catch (e: Exception) {
                        // ignore hex parse error
                    }
                }
            }
            i++
        }
        return out.toString()
    }

    private fun decodePdfString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = input.length
        while (i < len) {
            val c = input[i]
            if (c == '\\' && i + 1 < len) {
                val next = input[i + 1]
                if (next.isDigit() && i + 3 < len && input[i + 2].isDigit() && input[i + 3].isDigit()) {
                    try {
                        val octal = input.substring(i + 1, i + 4)
                        val charCode = octal.toInt(8)
                        sb.append(charCode.toChar())
                        i += 4
                        continue
                    } catch (e: Exception) {
                        // fallback
                    }
                }
                when (next) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '(' -> sb.append('(')
                    ')' -> sb.append(')')
                    '\\' -> sb.append('\\')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun cleanExtractedText(text: String): String {
        // Clean up redundant spaces and non-printable characters while preserving newlines
        val lines = text.split("\n")
        val cleanedLines = lines.map { line ->
            line.replace(Regex("\\s+"), " ").trim()
        }.filter { it.isNotEmpty() }
        return cleanedLines.joinToString("\n")
    }

    fun extractTextChunksByPages(pdfBytes: ByteArray, pageCount: Int): List<String> {
        val streams = mutableListOf<String>()
        var index = 0
        while (index < pdfBytes.size) {
            val streamStartIndex = findSequence(pdfBytes, "stream".toByteArray(), index)
            if (streamStartIndex == -1) break
            
            val objStartIndex = maxOf(0, streamStartIndex - 500)
            val headerString = String(pdfBytes.copyOfRange(objStartIndex, streamStartIndex), Charsets.US_ASCII)
            val isFlateFiltered = headerString.contains("/FlateDecode")
            
            val endStreamIndex = findSequence(pdfBytes, "endstream".toByteArray(), streamStartIndex)
            if (endStreamIndex == -1) break
            
            var contentStart = streamStartIndex + 6
            if (contentStart < pdfBytes.size && pdfBytes[contentStart] == '\r'.toByte()) contentStart++
            if (contentStart < pdfBytes.size && pdfBytes[contentStart] == '\n'.toByte()) contentStart++
            
            val contentEnd = endStreamIndex
            if (contentStart < contentEnd) {
                val streamBytes = pdfBytes.copyOfRange(contentStart, contentEnd)
                try {
                    val decompressedBytes = if (isFlateFiltered) {
                        decompressFlate(streamBytes)
                    } else {
                        streamBytes
                    }
                    val streamText = String(decompressedBytes, Charsets.UTF_8)
                    val extracted = parseTextFromStream(streamText)
                    val cleaned = cleanExtractedText(extracted)
                    if (cleaned.isNotEmpty()) {
                        streams.add(cleaned)
                    }
                } catch (e: Exception) {
                    // Ignore corrupted streams
                }
            }
            index = endStreamIndex + 9
        }

        if (streams.isEmpty()) return emptyList()

        // Map streams to P pages proportionally
        val p = maxOf(1, pageCount)
        val pagesText = List(p) { StringBuilder() }
        val s = streams.size

        for (i in 0 until s) {
            val pageIndex = ((i.toDouble() / s) * p).toInt().coerceIn(0, p - 1)
            if (pagesText[pageIndex].isNotEmpty()) {
                pagesText[pageIndex].append("\n")
            }
            pagesText[pageIndex].append(streams[i])
        }

        // Group into 15-page segments
        val segments = mutableListOf<String>()
        val segmentSize = 15
        for (startPage in 0 until p step segmentSize) {
            val endPage = minOf(startPage + segmentSize, p)
            val segmentBuilder = StringBuilder()
            for (pageIdx in startPage until endPage) {
                val pageText = pagesText[pageIdx].toString().trim()
                if (pageText.isNotEmpty()) {
                    if (segmentBuilder.isNotEmpty()) {
                        segmentBuilder.append("\n\n--- Page ${pageIdx + 1} ---\n\n")
                    } else {
                        segmentBuilder.append("--- Page ${pageIdx + 1} ---\n\n")
                    }
                    segmentBuilder.append(pageText)
                }
            }
            val segmentResult = segmentBuilder.toString().trim()
            if (segmentResult.isNotEmpty()) {
                segments.add(segmentResult)
            }
        }

        return segments
    }
}
