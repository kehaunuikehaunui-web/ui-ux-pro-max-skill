package com.khn.mp3.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Suppression définitive de fichiers audio du téléphone.
 *
 * - Android 11+ : une seule demande système pour tout le lot (obligatoire pour les fichiers d'autres apps).
 * - Android 10 : une autorisation système par fichier (RecoverableSecurityException).
 * - Android 8/9 : suppression directe (permission de stockage).
 */
class TrackDeleter(private val context: Context) {

    data class Outcome(
        val deleted: List<Track> = emptyList(),
        val needsApproval: List<Pair<Track, IntentSender>> = emptyList(),
        val failed: List<Track> = emptyList(),
    )

    /** URI MediaStore d'un titre (y compris un fichier importé qui se trouve dans le stockage partagé). */
    fun mediaStoreUri(track: Track): Uri? = when (track.source) {
        Track.Source.DEVICE -> track.uri
        Track.Source.IMPORTED -> if (Build.VERSION.SDK_INT >= 29) {
            runCatching { MediaStore.getMediaUri(context, track.uri) }.getOrNull()
        } else null
        Track.Source.EXTERNAL -> null
    }

    /** Android 11+ : demande système unique pour supprimer tous ces fichiers. */
    @RequiresApi(30)
    fun batchRequest(uris: List<Uri>): IntentSender =
        MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

    /** Android 11+ : mettre à la corbeille (`trashed` = true) ou restaurer (`trashed` = false). */
    @RequiresApi(30)
    fun trashRequest(uris: List<Uri>, trashed: Boolean): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, uris, trashed).intentSender

    /** Suppression sans fenêtre système (Android 8–10, ou documents importés). */
    fun deleteNow(tracks: List<Track>): Outcome {
        val deleted = ArrayList<Track>()
        val approval = ArrayList<Pair<Track, IntentSender>>()
        val failed = ArrayList<Track>()
        val resolver = context.contentResolver
        for (t in tracks) {
            when (t.source) {
                Track.Source.IMPORTED -> {
                    val ok = runCatching { DocumentsContract.deleteDocument(resolver, t.uri) }.getOrDefault(false)
                    if (ok) deleted += t else failed += t
                }
                Track.Source.DEVICE -> try {
                    if (resolver.delete(t.uri, null, null) > 0) deleted += t else failed += t
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                        approval += t to e.userAction.actionIntent.intentSender
                    } else {
                        failed += t
                    }
                }
                Track.Source.EXTERNAL -> failed += t
            }
        }
        return Outcome(deleted, approval, failed)
    }
}
