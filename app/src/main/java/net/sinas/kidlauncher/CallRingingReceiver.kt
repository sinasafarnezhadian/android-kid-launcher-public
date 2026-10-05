package net.sinas.kidlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

// Meldet klingelnde/beendete Anrufe an MainLauncherActivity, die daraufhin eine eigene
// Annehmen/Ablehnen-Oberfläche einblendet. Der naheliegendere Weg - den Anruf-Screen des
// System-Dialers per TelecomManager.showInCallScreen() nach vorne zu holen - ist auf diesem
// Gerät nachweislich ein Race: Telecom bindet zwar an den Dialer, dessen InCallActivity startet
// aber nur in ca. der Hälfte der Fälle tatsächlich (per Logcat/usagestats bestätigt), und der
// sonst übliche Heads-up-Notification-Fallback wird zusätzlich unterdrückt, weil eine per Lock
// Task gepinnte App (unser Launcher) im Fokus ist. Damit bleibt der Anruf dann komplett
// unsichtbar und unbedienbar. Die eigene Oberfläche hängt an keinem fremden Activity-Start mehr.
class CallRingingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(TAG, "onReceive: action=${intent.action}, state=$state")

        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Erfordert READ_CALL_LOG, sonst liefert der Broadcast diesen Extra seit Android 10 nicht.
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                MainLauncherActivity.notifyCallRinging(number)
            }
            TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK ->
                MainLauncherActivity.notifyCallNoLongerRinging()
        }
    }

    companion object {
        private const val TAG = "CallRingingReceiver"
    }
}
