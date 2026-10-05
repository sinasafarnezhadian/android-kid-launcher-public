package net.sinas.kidlauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

// Setzt den Schulmodus automatisch zu den im Eltern-Bereich festgelegten Uhrzeiten. AlarmManager statt
// eines periodischen Checks in MainLauncherActivity, weil der Zustand auch greifen muss, während der
// Bildschirm aus ist und die Activity nicht läuft - genau der Fall, in dem ein reiner onResume()-Check
// die Grenze verpassen würde.
// setAndAllowWhileIdle() statt setExactAndAllowWhileIdle(): Exakte Alarme erfordern seit Android 13
// bei targetSdk 34 eine manuelle Freigabe über die Systemeinstellungen (SCHEDULE_EXACT_ALARM wird
// Device-Owner-Apps NICHT automatisch gewährt, siehe SecurityException in der Git-Historie). Die
// inexakte Variante feuert ebenfalls zuverlässig im Doze-Modus, nur ohne Millisekunden-Garantie
// (kann um einige Minuten verschoben werden) - für Schulmodus an/aus unerheblich, braucht aber keine
// Sonderberechtigung und keinen zusätzlichen manuellen Schritt für die Eltern.
class SchoolModeScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ENABLE -> {
                MainLauncherActivity.applyScheduledSchoolMode(context, true)
                scheduleAlarms(context)
            }
            ACTION_DISABLE -> {
                MainLauncherActivity.applyScheduledSchoolMode(context, false)
                scheduleAlarms(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> scheduleAlarms(context)
        }
    }

    companion object {
        private const val TAG = "SchoolModeScheduleReceiver"
        private const val ACTION_ENABLE = "net.sinas.kidlauncher.action.SCHOOL_MODE_ENABLE"
        private const val ACTION_DISABLE = "net.sinas.kidlauncher.action.SCHOOL_MODE_DISABLE"

        // Nach dem Feuern eines Alarms wird hier direkt der nächste Tag für BEIDE Alarme neu berechnet -
        // der gerade gefeuerte landet automatisch auf morgen, der andere bleibt (falls noch nicht
        // erreicht) auf heute. Das ersetzt eine explizite "täglich wiederholen"-Logik.
        fun scheduleAlarms(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            cancelAlarms(context, alarmManager)

            if (!SchoolModeStore.isScheduleEnabled(context)) return

            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrence(SchoolModeStore.getStartMinutes(context)),
                    pendingIntentFor(context, ACTION_ENABLE)
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextOccurrence(SchoolModeStore.getEndMinutes(context)),
                    pendingIntentFor(context, ACTION_DISABLE)
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Zeitplan-Alarme konnten nicht gesetzt werden", e)
            }
        }

        private fun cancelAlarms(context: Context, alarmManager: AlarmManager) {
            alarmManager.cancel(pendingIntentFor(context, ACTION_ENABLE))
            alarmManager.cancel(pendingIntentFor(context, ACTION_DISABLE))
        }

        private fun pendingIntentFor(context: Context, action: String): PendingIntent {
            val intent = Intent(context, SchoolModeScheduleReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun nextOccurrence(minutesSinceMidnight: Int): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutesSinceMidnight / 60)
                set(Calendar.MINUTE, minutesSinceMidnight % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }
    }
}
