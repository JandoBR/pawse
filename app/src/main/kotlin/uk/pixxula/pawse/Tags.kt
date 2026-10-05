package uk.pixxula.pawse

import android.content.Context
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.SystemClock
import android.util.Log
import java.security.SecureRandom

/** How writing a tag went. */
sealed interface Written {
    data class Done(val id: String) : Written
    data object ReadOnly : Written
    data class TooSmall(val has: Int, val needs: Int) : Written
    data object NotNdef : Written

    /** Usually the tag was taken away too early. */
    data object Failed : Written
}

/** Reading and writing the tags that are keys. */
object Tags {
    private const val TAG = "Pawse"

    /** A random id for a new key. It identifies the tag; it is not a secret. */
    fun newId(): String {
        val bytes = ByteArray(8).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * What a key holds: its URI, and a record that makes Android hand the tag
     * to this app whatever else is in front.
     */
    private fun message(context: Context, id: String) = NdefMessage(
        NdefRecord.createUri(Uri.parse("pawse://key/$id")),
        NdefRecord.createApplicationRecord(context.packageName),
    )

    /** The key id a tag already carries, or null when it is not a Pawse tag. */
    fun read(tag: Tag): String? {
        val records = Ndef.get(tag)?.cachedNdefMessage?.records ?: return null
        return records.firstNotNullOfOrNull { record ->
            record.toUri()?.toString()?.let(Session::keyId)
        }
    }

    /** Writes a new key to [tag]. The tag is left rewritable. */
    fun write(context: Context, tag: Tag): Written {
        val id = newId()
        val message = message(context, id)
        val needs = message.toByteArray().size
        try {
            val ndef = Ndef.get(tag)
            if (ndef == null) {
                val blank = NdefFormatable.get(tag) ?: return Written.NotNdef
                blank.connect()
                blank.use { it.format(message) }
                return Written.Done(id)
            }
            ndef.connect()
            ndef.use {
                if (!it.isWritable) return Written.ReadOnly
                if (it.maxSize < needs) return Written.TooSmall(it.maxSize, needs)
                it.writeNdefMessage(message)
            }
            return Written.Done(id)
        } catch (e: Exception) {
            Log.w(TAG, "writing a tag failed", e)
            return Written.Failed
        }
    }

    // A tag still against the phone when the Keys screen lets go of it is
    // read again by Android and handed over as a scan. Scans are ignored for
    // a moment after the Keys screen has dealt with a tag, or a key would
    // start a session in the act of being made.

    @Volatile
    private var mutedUntil = 0L

    fun muteScans() {
        mutedUntil = SystemClock.elapsedRealtime() + MUTE_MILLIS
    }

    val scansMuted: Boolean
        get() = SystemClock.elapsedRealtime() < mutedUntil

    private const val MUTE_MILLIS = 4_000L
}
