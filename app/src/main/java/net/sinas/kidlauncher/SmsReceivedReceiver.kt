package net.sinas.kidlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

// Aktualisiert die Nachrichten-Box neben dem Foto sofort bei einer neuen SMS, statt nur bei
// onResume(). Liest den Absender/Text direkt aus den PDU-Extras des Broadcasts statt die
// SMS-Tabelle erneut abzufragen - vermeidet ein bekanntes Timing-Problem, bei dem der
// Content-Provider-Eintrag beim Eintreffen dieses Broadcasts noch nicht geschrieben ist.
class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val address = messages[0].originatingAddress ?: return
        // Mehrteilige SMS kommen als mehrere SmsMessage-Objekte für denselben Text an.
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        MainLauncherActivity.notifyMessageReceived(address, body)
    }
}
