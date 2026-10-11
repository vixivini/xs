package com.example.xs.util

object MediaUtils {
    fun isDirectMedia(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore("?")

        // Extensões comuns
        val videoExtensions = listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".m4v", ".m3u8", ".mpd")
        if (videoExtensions.any { path.endsWith(it) || lowerUrl.contains("$it?") }) return true

        // Padrões de links de stream/download direto
        if (lowerUrl.contains("turbo.cr") && (lowerUrl.contains("cdn.") || videoExtensions.any { lowerUrl.contains(it) })) return true
        if (lowerUrl.contains("b-cdn.net")) return true
        if ((lowerUrl.contains("bunkr") || lowerUrl.contains("bunkrr")) &&
            (lowerUrl.contains("/d/") || lowerUrl.contains("stream.") || lowerUrl.contains("cdn"))) return true
        if (lowerUrl.contains("gofile.io") && (lowerUrl.contains("/download/") || lowerUrl.contains("store"))) return true
        if (lowerUrl.contains("pixeldrain.com/api/file/")) return true

        return false
    }

    fun isDirectVideo(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore("?")
        val videoExtensions = listOf(".mp4", ".mov", ".webm", ".mkv", ".avi", ".m4v", ".m3u8", ".mpd")
        if (videoExtensions.any { path.endsWith(it) || lowerUrl.contains("$it?") }) return true
        if (lowerUrl.contains("turbo.cr") && (lowerUrl.contains("cdn.") || videoExtensions.any { lowerUrl.contains(it) })) return true
        if (lowerUrl.contains("b-cdn.net") && videoExtensions.any { lowerUrl.contains(it) }) return true
        return false
    }

    fun isWebViewVideo(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // Se conseguimos o link direto (MP4, M3U8, etc), NÃO usa WebView
        if (isDirectVideo(url)) return false

        // Turbo.cr só usa WebView como último recurso se não conseguirmos o link direto
        if (lowerUrl.contains("turbo.cr") && (lowerUrl.contains("/v/") || lowerUrl.contains("/f/") || lowerUrl.contains("/embed/"))) return true

        return (lowerUrl.contains("bunkr") || lowerUrl.contains("bunkrr") ||
               lowerUrl.contains("gofile.io") ||
               lowerUrl.contains("pixeldrain.com"))
    }

    fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm") ||
               lower.contains("turbo.cr") || lower.contains("pixeldrain.com/l/") || lower.contains("pixeldrain.com/api/file/")
    }

    fun isDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        // Só é stream direto se for um arquivo de vídeo real.
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm")
    }

    fun getReferer(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("bunkr") || lowerUrl.contains("b-cdn.net") -> {
                when {
                    lowerUrl.contains("bunkr.su") -> "https://bunkr.su/"
                    lowerUrl.contains("bunkr.si") -> "https://bunkr.si/"
                    lowerUrl.contains("bunkr.sk") -> "https://bunkr.sk/"
                    lowerUrl.contains("bunkr.black") -> "https://bunkr.black/"
                    else -> "https://bunkr.is/"
                }
            }
            lowerUrl.contains("gofile.io") -> "https://gofile.io/"
            lowerUrl.contains("pixeldrain.com") -> "https://pixeldrain.com/"
            lowerUrl.contains("turbo.cr") -> {
                // Para Turbo.cr em 2025/2026, o referer ideal é o próprio domínio turbo.cr ou a página de embed
                if (lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8")) "https://turbo.cr/"
                else if (url.contains("/embed/")) "https://turbo.cr/"
                else "https://simpcity.cr/"
            }
            else -> "https://simpcity.cr/"
        }
    }
}
