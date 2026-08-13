package it.droneskycheck.app.data.help

import it.droneskycheck.app.data.DscApiConfig
import java.net.URI

data class HelpResolvedImage(
    val url: String,
    val localDrawableName: String? = null
)

object HelpImageResolver {
    private val supportedExtensions = setOf("webp", "png", "jpg", "jpeg")
    private val relativePathPattern = Regex("^[A-Za-z0-9._/-]+$")

    fun resolve(
        value: String?,
        baseUrl: String = DscApiConfig.HelpImagesBaseUrl
    ): HelpResolvedImage? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.any { it.isISOControl() }) return null

        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.isAbsolute) {
            return resolveAbsolute(uri)
        }

        if (!isSafeRelativePath(raw)) return null
        return HelpResolvedImage(
            url = baseUrl.withTrailingSlash() + raw,
            localDrawableName = raw.substringAfterLast('/').substringBeforeLast('.')
        )
    }

    private fun resolveAbsolute(uri: URI): HelpResolvedImage? {
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.host.isNullOrBlank()) return null
        if (!hasSupportedExtension(uri.path.orEmpty())) return null
        return HelpResolvedImage(url = uri.toString())
    }

    private fun isSafeRelativePath(value: String): Boolean {
        if (value.startsWith("/") || value.startsWith("\\") || value.contains('\\')) return false
        if (value.contains("://") || value.contains('?') || value.contains('#')) return false
        if (!relativePathPattern.matches(value)) return false
        val segments = value.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
        return hasSupportedExtension(value)
    }

    private fun hasSupportedExtension(path: String): Boolean {
        val fileName = path.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in supportedExtensions
    }

    private fun String.withTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}
