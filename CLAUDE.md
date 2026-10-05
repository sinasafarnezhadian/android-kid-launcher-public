# Kid-Launcher (Android Device Owner Kiosk)

Eigenentwickelter Android-Launcher für ein **dediziertes Kind-Gerät**. Ziel: Kind sieht nur eine Whitelist an Apps, Rest ist komplett gesperrt. Zusätzlich: Kurzwahl für ausgehende Anrufe, Whitelist für eingehende Anrufe.

## Architektur-Entscheidung

Kein MDM, kein Root, kein Trial-and-Error-Ansatz. Nur Google-vorgesehene Bordmittel:

- **Device Owner Mode** (`DevicePolicyManager`) – volle Gerätekontrolle, per ADB einmalig gesetzt
- **Lock Task Mode** (`startLockTask()`) – sperrt auf definierte Paket-Whitelist
- **HOME-Launcher-Intent** – eigene App ersetzt den System-Launcher
- **CallScreeningService** (Rolle `ROLE_CALL_SCREENING`) – Whitelist für eingehende Anrufe

Kein anderer Ansatz (Accessibility-Service-Hacks, Screen-Pinning ohne Device Owner, etc.) wird verwendet — diese sind Umwege bzw. unzuverlässig.

## Harte Voraussetzung: Setup-Reihenfolge

Diese Reihenfolge ist **zwingend**, nicht optional:

1. Werksreset des Geräts
2. Setup-Assistent durchlaufen, **kein** Google-Konto hinzufügen
3. Entwickleroptionen aktivieren (7× auf Build-Nummer tippen), USB-Debugging an
4. App installieren, dann per ADB als Device Owner setzen:
   ```
   adb shell dpm set-device-owner com.deinpaket/.MyDeviceAdminReceiver
   ```
5. **Erst danach** optional Google-Konto hinzufügen (Device-Owner-Status bleibt erhalten, da schon gesetzt)
6. Standort aktivieren + „Find My Device" in den Google-Einstellungen einschalten
7. `ROLE_CALL_SCREENING` einmalig über Systemdialog an die App vergeben
8. Erst zum Schluss: `startLockTask()` in der Launcher-Activity aktivieren

Grund für Schritt 1–2: `dpm.set-device-owner` funktioniert nur auf einem Gerät ohne jedes bestehende Nutzerkonto — Sicherheitsschranke von Google, kein Workaround möglich.

## Sonderfall: Custom-ROM ohne vorinstallierte Google-Apps (z.B. LineageOS)

Die obige Setup-Reihenfolge setzt Stock-Android mit vorhandenen Google-Diensten voraus. Auf einem Custom-ROM (getestet: LineageOS 22 / Android 15, Pixel 2 „walleye") ohne GApps kommen vor Schritt 5 („Google-Konto hinzufügen") zusätzliche, zwingende Zwischenschritte dazu — sonst funktionieren Sync und „Find My Device" nicht, auch wenn alles andere (Device Owner, Lock Task, Kurzwahl) bereits läuft.

**A. GApps installieren — vor dem ersten Boot ins System, sonst Werksreset nötig**

- Passendes Paket besorgen (z.B. [MindTheGapps](https://github.com/MindTheGapps)) — **Architektur des Geräts beachten** (Pixel 2 = `arm64`, nicht `arm`)
- Im Recovery flashen, **bevor** man nach der ROM-Installation zum ersten Mal ins System bootet: Wer schon gebootet hat, muss zwingend erst per Wipe Data/Factory Reset zurück (GApps nachträglich auf ein bereits initialisiertes System zu flashen führt laut MindTheGapps-Doku zu Crashes)
- Bei „Signature verification failed (Error 21)“ im offiziellen LineageOS-Recovery: Menüpunkt „Disable signature verification“ aktivieren (GApps-Zips sind nicht mit dem LineageOS-OTA-Key signiert)
- `adb sideload` schlägt oft mit „connection failed: closed“ fehl, wenn das Recovery nicht exakt im Unterpunkt „Apply from ADB“ steht — zuverlässiger: Zip per `adb push <datei> /sdcard/` kopieren und im Recovery über „Apply from Storage“ flashen
- Device-Owner-Status übersteht einen reinen Data-Wipe nicht (App + `dpm set-device-owner` müssen danach neu gesetzt werden), einen GApps-Flash selbst aber schon (kein Wipe involviert)

**B. Nach Google-Konto-Hinzufügen: GMS fehlen Laufzeit-Berechtigungen**

Auf Stock-Firmware vergibt der Setup-Assistent Google Play Services automatisch Berechtigungen wie Kontakte/Standort/Konten — dieser Auto-Grant läuft aber nur, wenn man das Google-Konto **während** des Wizards hinzufügt. Da wir das laut Setup-Reihenfolge bewusst überspringen, bleiben diese Berechtigungen auf Custom-ROMs mit nachträglich geflashtem GApps leer, erkennbar an `Permission Denial`/`Missing permission`-Meldungen für `com.google.android.gms` im Logcat. Fix — nach Schritt 5 (Google-Konto hinzufügen) manuell nachziehen:

```
adb shell pm grant com.google.android.gms android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.google.android.gms android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.google.android.gms android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant com.google.android.gms android.permission.READ_CONTACTS
adb shell pm grant com.google.android.gms android.permission.WRITE_CONTACTS
adb shell pm grant com.google.android.gms android.permission.GET_ACCOUNTS
adb shell pm grant com.google.android.gms android.permission.READ_PHONE_STATE
adb shell pm grant com.google.android.gms android.permission.ACTIVITY_RECOGNITION
adb shell pm grant com.google.android.gms android.permission.READ_CALENDAR
adb shell pm grant com.google.android.gms android.permission.WRITE_CALENDAR
```

**C. „Find My Device“ findet das Gerät trotzdem nicht: Geräte-Zertifizierung**

Custom-ROM-Geräte gelten gegenüber Google standardmäßig als „uncertified“ — das blockiert „Find My Device“ unabhängig von A und B. Fix:

1. GSF Android-ID auslesen (braucht `adb root`, **nicht** identisch mit der SSAID aus `settings get secure android_id`):
   ```
   adb root
   adb shell "sqlite3 /data/data/com.google.android.gsf/databases/gservices.db 'select * from main;'" | grep android_id
   ```
2. Mit dem auf dem Gerät eingerichteten Google-Konto auf https://www.google.com/android/uncertified/ registrieren
3. Gerät neu starten, 15–30 Minuten (teils länger) auf Propagierung warten, dann erneut prüfen

## Kernkomponenten

### 1. Launcher-Activity (HOME-Intent)
```xml
<activity android:name=".MainLauncherActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### 2. DeviceAdminReceiver (Voraussetzung für Device Owner)
```xml
<receiver android:name=".MyDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### 3. App-Whitelist + Lock Task starten
```kotlin
val dpm = getSystemService(DevicePolicyManager::class.java)
val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

dpm.setLockTaskPackages(admin, arrayOf(
    "com.deinpaket",       // eigener Launcher
    "com.erlaubteapp1",
    "com.erlaubteapp2"
))

startLockTask()
```

### 4. Ausgehende Anrufe (Kurzwahl-Buttons im Launcher)
Kein echter Dialer erreichbar (nicht in Whitelist) → Launcher-UI selbst löst Anrufe aus:
```kotlin
dpm.setPermissionGrantState(admin, packageName,
    Manifest.permission.CALL_PHONE,
    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)

startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:+491701234567")))
```

### 5. Eingehende Anrufe (Whitelist via CallScreeningService)
```xml
<service android:name=".MyCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>
```
```kotlin
class MyCallScreeningService : CallScreeningService() {
    private val whitelist = setOf("+491701234567", "+491709999999")

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        val response = CallResponse.Builder()
        if (number !in whitelist) {
            response.setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
        }
        respondToCall(callDetails, response.build())
    }
}
```

### 6. Eingehende Anrufe: eigene Annehmen/Ablehnen-Oberfläche

Der naheliegende Ansatz — bei `RINGING` per `TelecomManager.showInCallScreen()` den Anruf-Screen des System-Dialers (aus Schritt 5/Whitelist bekannt) nach vorne holen — ist auf getestetem Gerät (LineageOS 22, Pixel 2) nachweislich unzuverlässig: Per Logcat/`dumpsys usagestats` bestätigt startet die `InCallActivity` des Dialers nur in ca. der Hälfte der Fälle tatsächlich, obwohl Telecom erfolgreich an den Dialer bindet (Race Condition im System-Dialer/Telecom-Stack, nicht beeinflussbar). Der sonst übliche Fallback — Android zeigt bei unterdrücktem Fullscreen-Intent eine normale Heads-up-Banner-Notification — greift im Kiosk zusätzlich **nicht**, weil Android Notifications grundsätzlich unterdrückt, wenn eine per Lock Task gepinnte App im Fokus ist. Ergebnis ohne diesen Fix: Anruf klingelt, aber weder Vollbild noch Banner erscheinen — für das Kind unsichtbar und unbedienbar.

Fix: Der Launcher zeigt bei `RINGING` selbst eine Annehmen/Ablehnen-Oberfläche, unabhängig vom Activity-Start des System-Dialers.

```kotlin
// CallRingingReceiver (BroadcastReceiver auf android.intent.action.PHONE_STATE)
when (state) {
    TelephonyManager.EXTRA_STATE_RINGING -> MainLauncherActivity.notifyCallRinging()
    TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK ->
        MainLauncherActivity.notifyCallNoLongerRinging()
}

// MainLauncherActivity
dpm.setPermissionGrantState(admin, packageName,
    Manifest.permission.ANSWER_PHONE_CALLS,
    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)

telecomManager.acceptRingingCall()  // Annehmen-Button
telecomManager.endCall()            // Ablehnen-Button
```

Die `CallScreeningService`-Whitelist aus Schritt 5 filtert weiterhin vor: Nur erlaubte Nummern erreichen `RINGING` und lösen die Oberfläche aus.

Anrufername/-foto: `EXTRA_INCOMING_NUMBER` aus dem Broadcast (erfordert `READ_CALL_LOG`, sonst leer seit Android 10) wird per `ContactsContract.PhoneLookup` gegen das Telefonbuch aufgelöst (`READ_CONTACTS` ist bereits aus Schritt „Kurzwahl“ gewährt). Ohne Treffer zeigt die Oberfläche die rohe Nummer, ohne Nummer `unknown_caller`.

Bildschirm aus/gesperrt bei Anrufbeginn: `Activity.setShowWhenLocked(true)` + `setTurnScreenOn(true)` (API 27+, kein Sonderfall nötig - minSdk ist 29) beim Einblenden der Oberfläche, zurückgesetzt beim Ausblenden. Zeigt die Oberfläche über dem Sperrbildschirm und weckt den Bildschirm, ohne ihn zu entsperren - außerhalb von Anrufen bleibt der normale Sperrbildschirm unverändert aktiv.

### 7. Letzte SMS der Kurzwahl-Kontakte neben dem Foto

Zeigt nur die eine neueste SMS über alle Kurzwahl-Nummern hinweg (nicht pro Kontakt), als „Name: Text". Beim App-Start/`onResume()`: `Telephony.Sms.Inbox.CONTENT_URI` (bewusst nicht das allgemeine `Telephony.Sms.CONTENT_URI`, das auch gesendete SMS einschließt - dort steht die Empfänger-Nummer ebenfalls im `address`-Feld und würde den Filter fälschlich passieren, siehe Git-Historie) absteigend nach Datum durchlaufen, beim ersten Treffer gegen eine Kurzwahl-Nummer abbrechen - Absendernummer und gespeicherte Nummer per `PhoneNumberUtils.compare()` statt exaktem String-Vergleich abgleichen, da Formatierung (Landesvorwahl, Leerzeichen) abweichen kann. Derselbe lose Vergleich (nicht `==`) wird auch für die Live-Aktualisierung (siehe unten) verwendet, um den Kontaktnamen zu ermitteln - sonst Absturzgefahr, wenn die Formate abweichen und der exakte Vergleich keinen Treffer findet. `READ_SMS` wie die anderen Berechtigungen per `setPermissionGrantState` gewährt. Box bleibt unsichtbar (`GONE`), wenn keine Kurzwahl-Kontakte existieren oder keine passende SMS gefunden wird.

Zusätzlicher, dauerhafter Inhaltsfilter: Nur Nachrichten, die das Stichwort `MESSAGE_FILTER_KEYWORD` (aktuell `"yy"`) enthalten, dürfen angezeigt werden - blendet Posteingangs-Rauschen (ALDI-TALK-Codes, Mailbox-Infos etc.) aus. Das Stichwort selbst wird per `stripFilterKeyword()` aus dem angezeigten Text entfernt, erscheint also nie sichtbar.

Live-Update bei neu eintreffender SMS: `SmsReceivedReceiver` (Broadcast auf `android.provider.Telephony.SMS_RECEIVED`, erfordert `RECEIVE_SMS` + `android:permission="android.permission.BROADCAST_SMS"` auf dem Receiver selbst - Standardmuster für SMS-Empfang) meldet sich direkt bei `MainLauncherActivity`, analog zu `CallRingingReceiver`. Liest Absender/Text über `Telephony.Sms.Intents.getMessagesFromIntent(intent)` aus den PDU-Extras des Broadcasts statt die Datenbank erneut abzufragen - vermeidet ein bekanntes Timing-Problem, bei dem der Content-Provider-Eintrag beim Eintreffen dieses Broadcasts noch nicht geschrieben ist (daher weiterhin `onResume()`-Abfrage zusätzlich nötig, z.B. für SMS, die eintrafen während die App nicht lief).

### 8. Schulmodus (Klingelton stummschalten)

Switch direkt in der Kind-Liste (nicht im PIN-geschützten Eltern-Bereich) — bewusst vom Kind selbst bedienbar. Zustand persistiert in `SchoolModeStore` (SharedPreferences) und bei jedem `setupKioskIfDeviceOwner()`-Durchlauf erneut angewendet (gleiches Muster wie die Berechtigungs-Grants - Android/andere Apps können Ringer-Zustand und Lautstärke jederzeit ändern).

```kotlin
if (enabled) {
    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT   // kein Ton, keine Vibration
} else {
    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxRingVolume * 0.3f).roundToInt(), 0)
    Settings.System.putInt(contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 1)
}
```

Anders als beim verworfenen Airplane-Mode-Versuch (siehe Git-Historie: `setGlobalSetting(AIRPLANE_MODE_ON)` wirft `SecurityException`, kein Workaround) ist Ringer-Steuerung als Device Owner sicher: `RINGER_MODE_SILENT` benötigt nur Benachrichtigungszugriff (`NotificationManager`-Zugriffsrechte), der Device-Owner-Apps automatisch gewährt wird. Trotzdem beide Zweige in try/catch, falls das auf einem ROM doch abweicht - dann bleibt der alte Zustand statt Absturz.

**`Settings.System.putInt(..., VIBRATE_WHEN_RINGING, 1)` braucht `WRITE_SETTINGS`, das Device Owner NICHT automatisch bekommt** (per Logcat bestätigt: `SecurityException: ... was not granted this permission: android.permission.WRITE_SETTINGS`) - anders als bei `RINGER_MODE_SILENT`, aber genau wie bei `SCHEDULE_EXACT_ALARM` unten: eine "Special App Access"-Berechtigung, kein normales Runtime-Permission, `setPermissionGrantState()` greift hier nicht. Einmaliger manueller Schritt nötig (Button „Einstellungszugriff für Vibration freigeben" im Eltern-Bereich → `Settings.ACTION_MANAGE_WRITE_SETTINGS`, analog zu Find-My-Device/`ROLE_CALL_SCREENING` in der Setup-Reihenfolge). Button blendet sich aus, sobald `Settings.System.canWrite()` true liefert.

Zeitplan: `SchoolModeScheduleReceiver` + `AlarmManager.setAndAllowWhileIdle()`, einstellbar im Eltern-Bereich (automatisch an/aus, Start-/Endzeit per `TimePickerDialog`). AlarmManager statt eines periodischen Checks in `onResume()`, weil der Zustand auch greifen muss, während der Bildschirm aus ist und die Activity nicht läuft - genau der Fall, den ein reiner Resume-Check verpassen würde. Jeder Alarm berechnet beim Feuern den nächsten Tag neu (kein `setRepeating()`, das unter Doze nicht exakt ist).

**Bewusst `setAndAllowWhileIdle()`, nicht `setExactAndAllowWhileIdle()`:** Erste Version nutzte die exakte Variante — schlug per Logcat bestätigt mit `SecurityException: Caller ... needs to hold android.permission.SCHEDULE_EXACT_ALARM` fehl. Anders als bei `RINGER_MODE_SILENT` oben wird diese Berechtigung Device-Owner-Apps **nicht** automatisch gewährt (targetSdk 34 erfordert seit Android 13 eine manuelle Freigabe über die Systemeinstellungen „Alarme & Erinnerungen“). Die inexakte Variante feuert ebenfalls zuverlässig im Doze-Modus (nur ohne Millisekunden-Garantie, kann um einige Minuten verschoben werden), braucht aber keine Sonderberechtigung und keinen zusätzlichen manuellen Schritt für die Eltern.

Ein `BOOT_COMPLETED`-Empfang im selben Receiver setzt die Alarme nach einem Neustart neu (Alarme überstehen keinen Reboot).

- **Notrufe (112) lassen sich nicht blocken** — Android erzwingt das systemseitig unabhängig von jeder Whitelist.
- **Find My Device** funktioniert normal weiter (Device Owner ≠ Work Profile, Play Services läuft unabhängig von der Lock-Task-Whitelist), erfordert aber ein nachträglich hinzugefügtes Google-Konto (siehe Setup-Reihenfolge).

## Offen / noch zu bauen

- Eltern-Ausstieg aus Lock Task Mode (z.B. versteckte Geste/PIN → `stopLockTask()` + Zugriff auf System-Settings), damit Find-My-Device-Status & Co. später ohne Neu-Setup geprüft werden können
- SMS-Whitelist (analog zu Call-Screening, über `ROLE_SMS`)
- UI der Launcher-App selbst (Icons/Namen der erlaubten Apps, Kurzwahl-Buttons)
- `dpm.setLockTaskFeatures()` Feinschliff (Statusbar, Home-Button während Lock Task erlauben/verbieten)
