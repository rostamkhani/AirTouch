package com.airtouch.app.player

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Track(val uri: String, val title: String)

/**
 * Songs the user picked with the system file picker. Read access is kept with persistable URI
 * grants, so no storage permission is needed. The list is stored in app-private storage.
 */
class MusicLibrary(private val context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val mutableTracks = MutableStateFlow(load())
    val tracks: StateFlow<List<Track>> = mutableTracks.asStateFlow()

    suspend fun addFiles(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val added = uris.mapNotNull { uri ->
            if (!keepAccess(resolver, uri)) return@mapNotNull null
            Track(uri.toString(), displayName(resolver, uri))
        }
        save(merge(added))
        added.size
    }

    suspend fun addFolder(tree: Uri): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (!keepAccess(resolver, tree)) return@withContext 0
        val found = ArrayList<Track>()
        scan(resolver, tree, DocumentsContract.getTreeDocumentId(tree), depth = 0, into = found)
        found.sortBy { it.title.lowercase() }
        save(merge(found))
        found.size
    }

    suspend fun remove(track: Track) = withContext(Dispatchers.IO) {
        save(mutableTracks.value.filterNot { it.uri == track.uri })
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        save(emptyList())
    }

    private fun merge(added: List<Track>): List<Track> {
        val known = mutableTracks.value.map { it.uri }.toHashSet()
        return mutableTracks.value + added.filter { known.add(it.uri) }
    }

    private fun scan(resolver: ContentResolver, tree: Uri, parentId: String, depth: Int, into: MutableList<Track>) {
        if (depth > MAX_DEPTH || into.size >= MAX_TRACKS) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && into.size < MAX_TRACKS) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    val mime = cursor.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scan(resolver, tree, id, depth + 1, into)
                    } else if (isAudio(mime, name)) {
                        into += Track(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), stripExtension(name))
                    }
                }
            }
        }
    }

    private fun keepAccess(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return stripExtension(name ?: uri.lastPathSegment.orEmpty())
    }

    private fun load(): List<Track> = runCatching {
        val array = JSONArray(file.readText())
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            Track(item.getString("uri"), item.optString("title"))
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Track>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("uri", it.uri).put("title", it.title)) }
        val temp = File(file.parentFile, "library.json.tmp")
        temp.writeText(array.toString())
        temp.renameTo(file)
        mutableTracks.value = list
    }

    companion object {
        private const val MAX_DEPTH = 4
        private const val MAX_TRACKS = 3_000
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "mka", "amr", "3gp")

        fun isAudio(mime: String, name: String): Boolean =
            mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS

        fun stripExtension(name: String): String {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) else name
        }
    }
}
