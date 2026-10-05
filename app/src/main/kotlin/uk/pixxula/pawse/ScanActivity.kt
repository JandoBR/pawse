package uk.pixxula.pawse

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast

/**
 * Receives a scanned tag (`pawse://key/<id>`), switches the session, says so
 * and finishes. It has no screen of its own, and works over any app.
 */
class ScanActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyId = intent?.dataString?.let(Session::keyId)
        if (keyId != null && !Tags.scansMuted) scanned(keyId)
        finish()
    }

    private fun scanned(keyId: String) {
        val store = Store.get(this)
        // A force-stop switches the service off without telling it, so a
        // session can be stored as on with nothing behind it.
        if (!Device.watcherSwitchedOn(this)) store.update(Session::endBecauseWatcherIsOff)

        Revive.ifStopped(this)
        val missing = Device.missing(this)
        val result = store.updateWith { Session.scan(it, keyId, Device.now(), missing) }
        Log.i(TAG, "scan: $result")
        when (result) {
            Scan.Started -> {
                vibrate(STARTED)
                toast(R.string.scan_started)
            }
            Scan.Ended -> {
                vibrate(ENDED)
                toast(R.string.scan_ended)
            }
            // The tag read twice in a row: the first read already answered.
            Scan.TooSoon -> Unit
            is Scan.NotReady -> open(MainActivity.intent(this))
            Scan.Unregistered -> open(MainActivity.intent(this, registerKey = keyId))
            Scan.Refused -> toast(R.string.scan_refused)
        }
    }

    private fun open(intent: Intent) {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toast(text: Int) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private fun vibrate(pattern: LongArray) {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private companion object {
        const val TAG = "Pawse"

        // Told apart without looking: one long buzz on, two short ones off.
        val STARTED = longArrayOf(0, 180)
        val ENDED = longArrayOf(0, 60, 90, 60)
    }
}
