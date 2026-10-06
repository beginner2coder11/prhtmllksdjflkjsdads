package com.web2app

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Serves an app built from a picked HTML file or website ZIP.
 *
 * The builder's CI unpacks that content into `assets/web/`, and this serves it over
 * `https://appassets.androidplatform.net/web/` rather than as a `file://` URL. The
 * origin is what makes the difference: under `file://` the browser blocks ES modules,
 * `fetch()` and the storage APIs outright, so a site that works everywhere else would
 * look broken inside the app — and would not match what the builder previewed, which
 * serves the same content the same way.
 */
object LocalContent {

    /** Reserved authority the asset loader answers on; nothing leaves the device. */
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PATH = "/web/"

    /** Where the content lives inside the APK's assets. */
    private const val ASSET_ROOT = "web"

    fun loaderFor(context: Context): WebViewAssetLoader =
        WebViewAssetLoader.Builder()
            .addPathHandler(PATH, AssetsSubdirectoryHandler(context))
            .build()

    /**
     * Serves the bundle out of `assets/web/`.
     *
     * The stock [WebViewAssetLoader.AssetsPathHandler] cannot do this: it resolves the
     * path left after the prefix against the assets ROOT, so `/web/site/index.html`
     * would be looked up as `assets/site/index.html` and every request would come back
     * empty (ERR_INVALID_RESPONSE). This one keeps the bundle in its own subdirectory,
     * out of the way of the app's own assets.
     */
    private class AssetsSubdirectoryHandler(
        context: Context,
    ) : WebViewAssetLoader.PathHandler {

        private val assets = context.applicationContext.assets

        override fun handle(path: String): WebResourceResponse? {
            // "../" in a request must never climb out of the bundle.
            val clean = path.trimStart('/').replace("//", "/")
            if (clean.split('/').any { it == ".." }) return notFound()
            val asset = if (clean.isEmpty()) "$ASSET_ROOT/index.html" else "$ASSET_ROOT/$clean"
            return try {
                WebResourceResponse(mimeOf(asset), null, assets.open(asset))
            } catch (e: IOException) {
                // A real 404 rather than a null-bodied response, so a missing file shows
                // as a missing file instead of a broken page.
                notFound()
            }
        }

        private fun notFound() = WebResourceResponse(
            "text/plain", "utf-8", 404, "Not Found", emptyMap(),
            ByteArrayInputStream(ByteArray(0))
        )

        /**
         * The WebView refuses to run a script or apply a stylesheet served under the
         * wrong type, so this has to be right — and MimeTypeMap doesn't know some of
         * the extensions a modern site uses.
         */
        private fun mimeOf(path: String): String {
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm" -> "text/html"
                "js", "mjs" -> "application/javascript"
                "css" -> "text/css"
                "json", "map" -> "application/json"
                "svg" -> "image/svg+xml"
                "wasm" -> "application/wasm"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                "ttf" -> "font/ttf"
                else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
            }
        }
    }

    /**
     * The URL for a page inside the bundle. [entry] is relative to the bundle root;
     * an absolute http(s) address is left alone, so a tab may still point at a real
     * website even in a locally-sourced app.
     */
    /**
     * The address the builder writes into a bundled app's config (and into its tab URLs)
     * while there is no real website. It is not a host that resolves — anything pointing
     * at it means "the page inside this bundle".
     */
    private const val PLACEHOLDER_ORIGIN = "https://local.file"

    /**
     * Resolves [entry] to something loadable.
     *
     * A bundled app's tabs are filled with the placeholder address, so an absolute URL
     * cannot simply be handed to the network: one pointing at [PLACEHOLDER_ORIGIN] is a
     * path inside the bundle and is rewritten to the asset origin. Any other absolute
     * URL is a genuine outside link and is left alone.
     */
    fun urlFor(entry: String): String {
        var clean = entry.trim()
        if (clean.startsWith(PLACEHOLDER_ORIGIN, true)) {
            clean = clean.removeRange(0, PLACEHOLDER_ORIGIN.length)
        } else if (clean.startsWith(ORIGIN, true)) {
            // A root-absolute link in a bundled page ("/pages/x.html") is resolved by
            // the WebView against the asset origin BEFORE we see it, which drops the
            // /web/ prefix the loader serves from and dead-ends in ERR_NAME_NOT_RESOLVED.
            // Already under /web/ → leave it; otherwise re-root it under the bundle.
            val rest = clean.substring(ORIGIN.length)
            if (rest.startsWith(PATH, true)) return clean
            clean = rest
        } else if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
            return clean
        }
        clean = clean.trimStart('/')
        val encoded = clean.split('/').joinToString("/") { Uri.encode(it) }
        return "$ORIGIN$PATH${encoded.ifEmpty { "index.html" }}"
    }

    /** Answers a WebView request out of the assets, or null to let it go to the network. */
    fun intercept(
        loader: WebViewAssetLoader?,
        request: WebResourceRequest,
    ): WebResourceResponse? = loader?.shouldInterceptRequest(request.url)

    /** True for URLs this serves, so in-app navigation can be told from outside links. */
    fun isLocal(url: String): Boolean = url.startsWith(ORIGIN)
}
