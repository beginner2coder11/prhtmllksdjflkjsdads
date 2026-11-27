package com.web2app

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.io.File

/**
 * Printing, saving and sharing for pages inside the app.
 *
 * A plain Android WebView silently drops four things every browser does:
 *  - `window.print()` does nothing;
 *  - an `<a download>` pointing at a `blob:` or `data:` URL (how pages hand over a PDF,
 *    Word or Excel file they built in JavaScript) is ignored, and the native download
 *    listener cannot read a blob anyway — it lives only inside the page;
 *  - `navigator.share` / `navigator.canShare` do not exist;
 *  - `window.open(blobUrl)` opens an empty popup, since a WebView cannot show a PDF.
 *
 * [SHIM] runs before any page script and routes each of these to [Bridge], which prints
 * through the Android print framework (printer or "Save as PDF"), saves into the phone's
 * Downloads folder, opens the share sheet, or opens the file in a viewer app.
 */
class WebFiles(private val activity: Activity, private val webView: WebView) {

    private val appContext: Context = activity.applicationContext

    /** False on old WebViews without document-start scripts; see [onPageStarted]. */
    private var documentStartInstalled = false

    fun install() {
        webView.addJavascriptInterface(Bridge(), BRIDGE)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, SHIM, setOf("*"))
            documentStartInstalled = true
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            onDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    /** Old WebViews without document-start scripts: inject as early as we can. */
    fun onPageStarted() {
        if (!documentStartInstalled) webView.evaluateJavascript(SHIM, null)
    }

    // ─── Native download listener ───────────────────────────────────────────

    private fun onDownload(url: String, userAgent: String?, disposition: String?, mime: String?) {
        // A blob:/data: URL the shim did not catch (e.g. location.href = blobUrl):
        // only the page can read it, so ask the page to hand the bytes over.
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            val name = URLUtil.guessFileName(url, disposition, mime)
            webView.evaluateJavascript(
                "window.__w2aSave&&window.__w2aSave(${JSONObject.quote(url)},${JSONObject.quote(name)})",
                null
            )
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val name = URLUtil.guessFileName(url, disposition, mime)
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                userAgent?.let { addRequestHeader("User-Agent", it) }
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                } else {
                    setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, name)
                }
            }
            (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            toast(appContext.getString(R.string.files_downloading, name))
        } catch (e: Exception) {
            toast(appContext.getString(R.string.files_failed))
        }
    }

    // ─── JavaScript bridge (called on a WebView background thread) ──────────

    private inner class Bridge {

        @JavascriptInterface
        fun print(title: String?) {
            activity.runOnUiThread { printPage(title) }
        }

        @JavascriptInterface
        fun saveFile(base64: String, name: String?, mime: String?) {
            val file = decode(base64, name, mime) ?: return failed()
            val saved = try {
                saveToDownloads(file)
            } catch (e: Exception) {
                null
            } ?: return failed()
            activity.runOnUiThread { showSaved(file.name, saved, file.mime) }
        }

        @JavascriptInterface
        fun openFile(base64: String, name: String?, mime: String?) {
            val file = decode(base64, name, mime) ?: return failed()
            val uri = try { cacheUri(file) } catch (e: Exception) { null } ?: return failed()
            activity.runOnUiThread { view(uri, file.mime) }
        }

        /** `{title, text, url, files:[{name, type, data}]}` from the navigator.share shim. */
        @JavascriptInterface
        fun share(json: String) {
            val data = try { JSONObject(json) } catch (e: Exception) { return failed() }
            val title = data.optString("title")
            val text = listOf(data.optString("text"), data.optString("url"))
                .filter { it.isNotBlank() }.joinToString("\n")
            val list = data.optJSONArray("files")
            val uris = ArrayList<Uri>()
            var mime: String? = null
            for (i in 0 until (list?.length() ?: 0)) {
                val f = list!!.getJSONObject(i)
                val file = decode(f.optString("data"), f.optString("name"), f.optString("type"))
                    ?: continue
                uris += try { cacheUri(file) } catch (e: Exception) { continue }
                mime = if (mime == null || mime == file.mime) file.mime else "*/*"
            }
            activity.runOnUiThread { shareSheet(title, text, uris, mime) }
        }

        @JavascriptInterface
        fun failed() {
            toast(appContext.getString(R.string.files_failed))
        }
    }

    // ─── Actions ────────────────────────────────────────────────────────────

    private fun printPage(title: String?) {
        val jobName = title?.takeIf { it.isNotBlank() }
            ?: activity.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Document"
        try {
            val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(
                jobName,
                webView.createPrintDocumentAdapter(jobName),
                PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
            )
        } catch (e: Exception) {
            toast(appContext.getString(R.string.files_failed))
        }
    }

    private class Decoded(val name: String, val mime: String, val bytes: ByteArray)

    private fun decode(base64: String, rawName: String?, rawMime: String?): Decoded? {
        val bytes = try { Base64.decode(base64, Base64.DEFAULT) } catch (e: Exception) { return null }
        var mime = rawMime?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        var name = (rawName ?: "").substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1f:*?\"<>|]"), "_").trim().trimStart('.')
        if (name.isEmpty()) name = "download"
        val map = MimeTypeMap.getSingleton()
        val ext = name.substringAfterLast('.', "").lowercase()
        if (mime.isEmpty() || mime == "application/octet-stream") {
            mime = map.getMimeTypeFromExtension(ext) ?: mime.ifEmpty { "application/octet-stream" }
        }
        if (ext.isEmpty()) map.getExtensionFromMimeType(mime)?.let { name = "$name.$it" }
        return Decoded(name.take(120), mime, bytes)
    }

    /** Writes into the public Downloads folder (app's own Downloads dir before Android 10). */
    private fun saveToDownloads(file: Decoded): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, file.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(file.bytes) } ?: return null
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri
        }
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        dir.mkdirs()
        val out = uniqueFile(dir, file.name)
        out.writeBytes(file.bytes)
        return FileProvider.getUriForFile(appContext, authority(), out)
    }

    /** A private copy for viewing/sharing; the shared cache folder is cleared each time. */
    private fun cacheUri(file: Decoded): Uri {
        val dir = File(appContext.cacheDir, CACHE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach {
            if (System.currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L) it.delete()
        }
        val out = uniqueFile(dir, file.name)
        out.writeBytes(file.bytes)
        return FileProvider.getUriForFile(appContext, authority(), out)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var n = 1
        while (f.exists()) f = File(dir, "$base ($n)$ext").also { n++ }
        return f
    }

    private fun showSaved(name: String, uri: Uri, mime: String) {
        val msg = appContext.getString(R.string.files_saved, name)
        Snackbar.make(webView, msg, 6000)
            .setAction(R.string.files_open) { view(uri, mime) }
            .show()
    }

    private fun view(uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No viewer for this type: the share sheet still lets them save or send it.
            shareSheet("", "", arrayListOf(uri), mime)
        }
    }

    private fun shareSheet(title: String, text: String, uris: ArrayList<Uri>, mime: String?) {
        val send = when {
            uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
            else -> Intent(Intent.ACTION_SEND)
        }.apply {
            type = mime ?: "text/plain"
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
            if (uris.isNotEmpty()) {
                clipData = ClipData.newRawUri(null, uris[0]).also { c ->
                    uris.drop(1).forEach { c.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            activity.startActivity(Intent.createChooser(send, title.ifBlank { null }))
        } catch (e: Exception) {
            toast(appContext.getString(R.string.files_failed))
        }
    }

    private fun authority() = "${appContext.packageName}.webfiles"

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val BRIDGE = "Web2AppFiles"
        private const val CACHE_DIR = "web_files"

        /**
         * Page-side half. Kept free of `$` so it can live in a Kotlin raw string.
         *  - window.print is pinned to the native print dialog: pages that swap it for a
         *    "make an image PDF" workaround on phones get the real dialog instead.
         *  - URL.createObjectURL is wrapped so a blob is still readable after the page
         *    revokes its URL (most do so ~0.5 s after clicking the link).
         */
        private val SHIM = """
(function(){
  if (window.__w2aFiles || !window.Web2AppFiles) return;
  window.__w2aFiles = 1;
  var N = window.Web2AppFiles;
  var blobs = {};
  var mk = URL.createObjectURL, rv = URL.revokeObjectURL;
  URL.createObjectURL = function(o){ var u = mk.apply(URL, arguments); if (o instanceof Blob) blobs[u] = o; return u; };
  URL.revokeObjectURL = function(u){ var r = rv.apply(URL, arguments); setTimeout(function(){ delete blobs[u]; }, 60000); return r; };

  function getBlob(url){
    if (blobs[url]) return Promise.resolve(blobs[url]);
    return fetch(url).then(function(r){ return r.blob(); });
  }
  function b64(blob){
    return new Promise(function(res, rej){
      var r = new FileReader();
      r.onload = function(){ var s = String(r.result); res(s.slice(s.indexOf(',') + 1)); };
      r.onerror = function(){ rej(r.error); };
      r.readAsDataURL(blob);
    });
  }
  function nameOf(url, given){
    if (given) return given;
    if (/^data:/i.test(url)) return 'download';
    try { var p = new URL(url).pathname.split('/').pop(); if (p && p.indexOf('.') > 0) return decodeURIComponent(p); } catch (e) {}
    return 'download';
  }
  function local(h){ return /^(blob:|data:)/i.test(h || ''); }
  function save(url, name){
    getBlob(url).then(function(b){ return b64(b).then(function(d){ N.saveFile(d, nameOf(url, name), b.type || ''); }); })
      .catch(function(){ N.failed(); });
  }
  window.__w2aSave = save;

  var printer = function(){ N.print(document.title || ''); };
  try { Object.defineProperty(window, 'print', { configurable: false, enumerable: true, get: function(){ return printer; }, set: function(){} }); }
  catch (e) { window.print = printer; }

  var click = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    if (this.hasAttribute('download') && local(this.href)) { save(this.href, this.getAttribute('download')); return; }
    return click.apply(this, arguments);
  };
  document.addEventListener('click', function(e){
    if (e.defaultPrevented) return;
    var a = e.target && e.target.closest && e.target.closest('a[href]');
    if (a && local(a.href) && a.hasAttribute('download')) {
      e.preventDefault(); save(a.href, a.getAttribute('download'));
    }
  }, false);

  var open = window.open;
  window.open = function(u){
    var s = u == null ? '' : String(u);
    if (local(s)) {
      var known = blobs[s];
      var html = known ? /html/i.test(known.type) : /^data:text\/html/i.test(s);
      if (!html) {
        getBlob(s).then(function(b){ return b64(b).then(function(d){ N.openFile(d, nameOf(s, ''), b.type || ''); }); })
          .catch(function(){ N.failed(); });
        return { closed: false, close: function(){}, focus: function(){}, blur: function(){}, postMessage: function(){} };
      }
    }
    return open.apply(window, arguments);
  };

  if (!navigator.share) {
    navigator.canShare = function(d){ return !!d && !!((d.files && d.files.length) || d.url || d.text || d.title); };
    navigator.share = function(d){
      d = d || {};
      var files = Array.prototype.slice.call(d.files || []);
      return Promise.all(files.map(function(f){
        return b64(f).then(function(x){ return { name: f.name || 'file', type: f.type || '', data: x }; });
      })).then(function(list){
        N.share(JSON.stringify({ title: d.title || '', text: d.text || '', url: d.url || '', files: list }));
      });
    };
  }
})();
""".trimIndent()
    }
}
