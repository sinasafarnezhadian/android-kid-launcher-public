package net.sinas.kidlauncher

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainLauncherActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var featuredPhotoContainer: View
    private lateinit var featuredPhoto: ImageView
    private lateinit var featuredPhotoPlaceholder: TextView
    private lateinit var lastMessageText: TextView
    private lateinit var itemListContainer: LinearLayout
    private lateinit var burgerMenuButton: TextView
    private lateinit var parentOverlay: View
    private lateinit var overlayManageSpeedDialButton: Button
    private lateinit var overlayChangePinButton: Button
    private lateinit var overlayOpenSettingsButton: Button
    private lateinit var overlayToggleKioskButton: Button
    private lateinit var overlayGrantWriteSettingsButton: Button
    private lateinit var schoolModeRowContent: View
    private lateinit var schoolModeSwitch: Switch
    private lateinit var scheduleEnabledSwitch: Switch
    private lateinit var scheduleStartTimeButton: Button
    private lateinit var scheduleEndTimeButton: Button
    private lateinit var overlayCloseButton: Button
    private lateinit var incomingCallOverlay: View
    private lateinit var incomingCallerPhoto: ImageView
    private lateinit var incomingCallerName: TextView
    private lateinit var acceptCallButton: Button
    private lateinit var declineCallButton: Button

    private var parentModeUnlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        setContentView(R.layout.activity_main)
        hideStatusBar()
        avoidNavigationBarOverlap()

        statusText = findViewById(R.id.statusText)
        featuredPhotoContainer = findViewById(R.id.featuredPhotoContainer)
        featuredPhoto = findViewById(R.id.featuredPhoto)
        featuredPhotoPlaceholder = findViewById(R.id.featuredPhotoPlaceholder)
        lastMessageText = findViewById(R.id.lastMessageText)
        itemListContainer = findViewById(R.id.itemListContainer)
        burgerMenuButton = findViewById(R.id.burgerMenuButton)
        parentOverlay = findViewById(R.id.parentOverlay)
        overlayManageSpeedDialButton = findViewById(R.id.overlayManageSpeedDialButton)
        overlayChangePinButton = findViewById(R.id.overlayChangePinButton)
        overlayOpenSettingsButton = findViewById(R.id.overlayOpenSettingsButton)
        overlayToggleKioskButton = findViewById(R.id.overlayToggleKioskButton)
        overlayGrantWriteSettingsButton = findViewById(R.id.overlayGrantWriteSettingsButton)
        schoolModeRowContent = findViewById(R.id.schoolModeRowContent)
        schoolModeSwitch = findViewById(R.id.schoolModeSwitch)
        scheduleEnabledSwitch = findViewById(R.id.scheduleEnabledSwitch)
        scheduleStartTimeButton = findViewById(R.id.scheduleStartTimeButton)
        scheduleEndTimeButton = findViewById(R.id.scheduleEndTimeButton)
        overlayCloseButton = findViewById(R.id.overlayCloseButton)
        incomingCallOverlay = findViewById(R.id.incomingCallOverlay)
        incomingCallerPhoto = findViewById(R.id.incomingCallerPhoto)
        incomingCallerName = findViewById(R.id.incomingCallerName)
        acceptCallButton = findViewById(R.id.acceptCallButton)
        declineCallButton = findViewById(R.id.declineCallButton)

        featuredPhotoContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        featuredPhotoContainer.clipToOutline = true
        featuredPhotoContainer.setOnClickListener { showParentPinDialog { launchPhotoPicker() } }

        burgerMenuButton.setOnClickListener { showParentPinDialog { showParentOverlay() } }
        overlayManageSpeedDialButton.setOnClickListener {
            parentOverlay.visibility = View.GONE
            startActivity(Intent(this, SpeedDialManagerActivity::class.java))
        }
        overlayChangePinButton.setOnClickListener { showChangePinDialog() }
        overlayOpenSettingsButton.setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        overlayToggleKioskButton.setOnClickListener { onToggleKioskClicked() }
        overlayGrantWriteSettingsButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        overlayCloseButton.setOnClickListener { parentOverlay.visibility = View.GONE }

        // Bewusst ohne PIN-Schutz direkt in der Kind-Liste: Schulmodus soll vom Kind selbst
        // ein-/ausschaltbar sein, nicht nur von den Eltern. Switch selbst ist nicht klickbar
        // (siehe Layout) - die ganze Zeile reagiert einheitlich wie die anderen Listenzeilen.
        val schoolModeEnabled = SchoolModeStore.isEnabled(this)
        schoolModeSwitch.isChecked = schoolModeEnabled
        updateSchoolModeRowBackground(schoolModeEnabled)
        schoolModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            SchoolModeStore.setEnabled(this, isChecked)
            applyRingerSettings(this, isChecked)
            updateSchoolModeRowBackground(isChecked)
        }
        schoolModeRowContent.setOnClickListener { schoolModeSwitch.toggle() }

        scheduleEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            SchoolModeStore.setScheduleEnabled(this, isChecked)
            SchoolModeScheduleReceiver.scheduleAlarms(this)
        }
        scheduleStartTimeButton.setOnClickListener {
            showTimePicker(SchoolModeStore.getStartMinutes(this)) { minutes ->
                SchoolModeStore.setStartMinutes(this, minutes)
                scheduleStartTimeButton.text = getString(R.string.school_mode_schedule_start_format, formatMinutes(minutes))
                SchoolModeScheduleReceiver.scheduleAlarms(this)
            }
        }
        scheduleEndTimeButton.setOnClickListener {
            showTimePicker(SchoolModeStore.getEndMinutes(this)) { minutes ->
                SchoolModeStore.setEndMinutes(this, minutes)
                scheduleEndTimeButton.text = getString(R.string.school_mode_schedule_end_format, formatMinutes(minutes))
                SchoolModeScheduleReceiver.scheduleAlarms(this)
            }
        }
        SchoolModeScheduleReceiver.scheduleAlarms(this)

        acceptCallButton.setOnClickListener { acceptRingingCall() }
        declineCallButton.setOnClickListener { declineRingingCall() }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        hideStatusBar()
        if (!parentModeUnlocked) {
            setupKioskIfDeviceOwner()
        }
        renderList()
        renderFeaturedPhoto()
        renderLastMessage()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    private fun showIncomingCallOverlay(number: String?) {
        // Weckt den Bildschirm und zeigt die Oberfläche über dem Sperrbildschirm an, ohne ihn zu
        // entsperren - genau wie beim normalen Anruf-Screen. Wird in hideIncomingCallOverlay()
        // wieder zurückgesetzt, damit der Sperrbildschirm außerhalb von Anrufen normal greift.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val contact = number?.let { lookupContactByNumber(it) }
        incomingCallerName.text = contact?.name ?: number ?: getString(R.string.unknown_caller)

        val photoBitmap = contact?.photoUri?.let { photoUri ->
            try {
                contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Anrufer-Foto konnte nicht geladen werden", e)
                null
            }
        }
        if (photoBitmap != null) {
            incomingCallerPhoto.setImageBitmap(photoBitmap)
        } else {
            incomingCallerPhoto.setImageDrawable(null)
        }

        incomingCallOverlay.visibility = View.VISIBLE
    }

    private data class ContactMatch(val name: String, val photoUri: String?)

    // Umgekehrte Rufnummernsuche im Telefonbuch (REVERSE-Lookup), analog zur Kontaktauswahl in
    // SpeedDialManagerActivity, hier aber anhand der Nummer statt einer vom Nutzer gewählten Contact-URI.
    private fun lookupContactByNumber(number: String): ContactMatch? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val name = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
                val photoUri = photoIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
                if (!name.isNullOrBlank()) return ContactMatch(name, photoUri)
            }
        }
        return null
    }

    private fun hideIncomingCallOverlay() {
        incomingCallOverlay.visibility = View.GONE
        setShowWhenLocked(false)
        setTurnScreenOn(false)
    }

    private fun acceptRingingCall() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        try {
            telecomManager?.acceptRingingCall()
        } catch (e: SecurityException) {
            Log.w(TAG, "acceptRingingCall() fehlgeschlagen", e)
        }
        hideIncomingCallOverlay()
    }

    private fun declineRingingCall() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        try {
            telecomManager?.endCall()
        } catch (e: SecurityException) {
            Log.w(TAG, "endCall() fehlgeschlagen", e)
        }
        hideIncomingCallOverlay()
    }

    private fun updateSchoolModeRowBackground(enabled: Boolean) {
        schoolModeRowContent.setBackgroundColor(Color.parseColor(if (enabled) "#283524" else "#2C0F0F"))
    }

    private fun showTimePicker(initialMinutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            true
        ).show()
    }

    companion object {
        private const val TAG = "MainLauncherActivity"
        private const val REQUEST_PICK_PHOTO = 2001

        // Nur Nachrichten mit diesem Stichwort im Text dürfen in der Box neben dem Foto erscheinen -
        // blendet Rauschen im Posteingang (ALDI-TALK-Codes, Mailbox-Infos etc.) dauerhaft aus. Das
        // Stichwort selbst wird aus dem angezeigten Text entfernt, siehe stripFilterKeyword().
        private const val MESSAGE_FILTER_KEYWORD = "yy"

        fun formatMinutes(minutes: Int): String = String.format("%02d:%02d", minutes / 60, minutes % 60)

        // RINGER_MODE_SILENT deckt "Klingelton 0 + Vibration aus" in einem Zustand ab (per Definition
        // kein Ton und keine Vibration). Device-Owner-Apps erhalten den nötigen Benachrichtigungszugriff
        // (isNotificationPolicyAccessGranted) automatisch, trotzdem sicherheitshalber try/catch -
        // siehe Git-Historie zum Absturz beim ersten Schulmodus-Versuch (setGlobalSetting(AIRPLANE_MODE_ON)).
        // Companion statt Instanzmethode, da SchoolModeScheduleReceiver das auch ohne laufende
        // Activity anwenden können muss (Alarm kann feuern, während die App nicht im Vordergrund ist).
        fun applyRingerSettings(context: Context, enabled: Boolean) {
            val audioManager = context.getSystemService(AudioManager::class.java) ?: return
            try {
                if (enabled) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    val maxRingVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxRingVolume * 0.3f).roundToInt(), 0)
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 1)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Vibration beim Klingeln konnte nicht aktiviert werden", e)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Schulmodus konnte nicht angewendet werden", e)
            }
        }

        // Von SchoolModeScheduleReceiver bei einem Zeitplan-Alarm aufgerufen. Läuft die Activity
        // gerade (Regelfall dank Lock Task), reicht es, den Switch umzuschalten - der bestehende
        // OnCheckedChangeListener übernimmt Persistieren, Anwenden und die Zeilenfarbe. Ohne laufende
        // Activity (z.B. kurz nach einem Reboot) wird direkt persistiert und angewendet.
        fun applyScheduledSchoolMode(context: Context, enabled: Boolean) {
            val activeInstance = instance
            if (activeInstance != null) {
                activeInstance.schoolModeSwitch.isChecked = enabled
            } else {
                SchoolModeStore.setEnabled(context, enabled)
                applyRingerSettings(context, enabled)
            }
        }

        // Da der Launcher per Lock Task dauerhaft im Vordergrund gepinnt ist, existiert stets
        // höchstens eine Instanz - CallRingingReceiver nutzt das, um die Annehmen/Ablehnen-Oberfläche
        // direkt anzusteuern, ohne einen eigenen Broadcast-Mechanismus aufzubauen.
        private var instance: MainLauncherActivity? = null

        fun notifyCallRinging(number: String?) {
            instance?.showIncomingCallOverlay(number)
        }

        fun notifyCallNoLongerRinging() {
            instance?.hideIncomingCallOverlay()
        }

        fun notifyMessageReceived(address: String, body: String) {
            instance?.showMessageIfFromSpeedDial(address, body)
        }

        // Packages, die zwar für Lock Task freigegeben sein müssen, aber keine eigene Zeile in der
        // Kind-Liste bekommen sollen (nur intern von Android für Systemfunktionen gestartet).
        // Beide Dialer-Packages nötig: com.android.dialer ist der AOSP/LineageOS-Standarddialer
        // (auf diesem Gerät der tatsächlich aktive), com.google.android.dialer der von MindTheGapps
        // mitgelieferte - welcher als Default-Phone-App gilt, ist geräte-/ROM-abhängig.
        private const val AOSP_DIALER_PACKAGE = "com.android.dialer"
        private const val GOOGLE_DIALER_PACKAGE = "com.google.android.dialer"
        private val HIDDEN_FROM_LIST = setOf(AOSP_DIALER_PACKAGE, GOOGLE_DIALER_PACKAGE)

        // TODO: durch weitere erlaubte Apps ergänzen (Package-Namen der Ziel-Apps)
        private val ALLOWED_PACKAGES = arrayOf(
            BuildConfig.APPLICATION_ID, // eigener Launcher muss immer enthalten sein
            "com.google.android.contacts",
            // Zeigt die In-Call-UI bei ausgehenden Kurzwahl-Anrufen (ACTION_CALL) an; ohne
            // Whitelist-Eintrag blockiert Lock Task den Start ("Attempted Lock Task Mode violation").
            AOSP_DIALER_PACKAGE,
            GOOGLE_DIALER_PACKAGE
        )
    }

    // "Vollbild wenn möglich": blendet die Statusleiste aus. LOCK_TASK_FEATURE_SYSTEM_INFO (siehe
    // setupKioskIfDeviceOwner) erlaubt der Leiste weiterhin, per Swipe-Geste transient eingeblendet
    // zu werden - das steht dazu nicht im Widerspruch, es geht hier nur um die Standardanzeige.
    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    // setDecorFitsSystemWindows(false) lässt den Inhalt auch hinter die (weiterhin sichtbare)
    // Navigationsleiste laufen - ohne das hier würde z.B. der Burger-Button unten teilweise
    // dahinter verschwinden. Nur die Statusleiste soll ausgeblendet sein, nicht die Navigationsleiste.
    private fun avoidNavigationBarOverlap() {
        val rootContainer = findViewById<View>(R.id.rootContainer)
        rootContainer.setOnApplyWindowInsetsListener { view, insets ->
            val bottomInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            insets
        }
    }

    private fun showParentPinDialog(onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.parent_pin_title)
            .setView(input)
            .setPositiveButton(R.string.parent_pin_confirm) { _, _ ->
                if (input.text.toString() == ParentPinStore.getPin(this)) {
                    onSuccess()
                } else {
                    Toast.makeText(this, R.string.parent_pin_wrong, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderFeaturedPhoto() {
        if (FeaturedPhotoStore.hasPhoto(this)) {
            val bitmap = BitmapFactory.decodeFile(FeaturedPhotoStore.getFile(this).path)
            if (bitmap != null) {
                featuredPhoto.setImageBitmap(bitmap)
                featuredPhotoPlaceholder.visibility = View.GONE
                return
            }
        }
        featuredPhoto.setImageDrawable(null)
        featuredPhotoPlaceholder.visibility = View.VISIBLE
    }

    // Zeigt nur die EINE neueste SMS unter allen Kurzwahl-Nummern, nicht pro Kontakt - daher wird
    // die Inbox einmal absteigend nach Datum durchlaufen und beim ersten Treffer abgebrochen.
    // PhoneNumberUtils.compare() statt exaktem String-Vergleich, weil die Absendernummer aus der
    // SMS-Tabelle und die in der Kurzwahl gespeicherte Nummer unterschiedlich formatiert sein können
    // (mit/ohne Landesvorwahl, Leerzeichen, etc.).
    private fun renderLastMessage() {
        val contacts = SpeedDialStore.load(this)
        if (contacts.isEmpty()) {
            lastMessageText.visibility = View.GONE
            return
        }

        val formatted = try {
            findLastMessage(contacts)
        } catch (e: SecurityException) {
            Log.w(TAG, "Kein Zugriff auf SMS-Posteingang", e)
            null
        }

        showMessage(formatted)
    }

    private fun showMessage(body: String?) {
        if (body.isNullOrBlank()) {
            lastMessageText.visibility = View.GONE
        } else {
            lastMessageText.text = body
            lastMessageText.visibility = View.VISIBLE
        }
    }

    // Von SmsReceivedReceiver bei einer frisch eingetroffenen SMS aufgerufen - aktualisiert die
    // Anzeige sofort, statt auf das nächste onResume() zu warten.
    private fun showMessageIfFromSpeedDial(address: String, body: String) {
        if (!body.contains(MESSAGE_FILTER_KEYWORD)) return
        val match = SpeedDialStore.load(this).firstOrNull { PhoneNumberUtils.compare(address, it.number) }
        if (match != null) {
            showMessage("${match.label}: ${stripFilterKeyword(body)}")
        }
    }

    // Entfernt das Filter-Stichwort selbst aus dem angezeigten Text - es dient nur zum Aussortieren,
    // nicht als sichtbarer Teil der Nachricht.
    private fun stripFilterKeyword(body: String): String =
        body.replace(MESSAGE_FILTER_KEYWORD, "").replace(Regex("\\s{2,}"), " ").trim()

    private fun findLastMessage(contacts: List<SpeedDialContact>): String? {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
        // Inbox.CONTENT_URI statt des allgemeinen CONTENT_URI, das auch gesendete SMS einschließt -
        // bei einer gesendeten SMS steht die Empfänger-Kurzwahlnummer ebenfalls im address-Feld,
        // würde den Filter also fälschlich passieren und selbst verschickte Nachrichten anzeigen.
        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
            if (addressIndex < 0 || bodyIndex < 0) return null

            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex) ?: continue
                if (!body.contains(MESSAGE_FILTER_KEYWORD)) continue
                val address = cursor.getString(addressIndex) ?: continue
                val match = contacts.firstOrNull { PhoneNumberUtils.compare(address, it.number) }
                if (match != null) {
                    return "${match.label}: ${stripFilterKeyword(body)}"
                }
            }
        }
        return null
    }

    private fun launchPhotoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_PHOTO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_PHOTO || resultCode != RESULT_OK) return

        val photoUri = data?.data ?: return
        if (FeaturedPhotoStore.savePhoto(this, photoUri)) {
            renderFeaturedPhoto()
        } else {
            Toast.makeText(this, R.string.featured_photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showParentOverlay() {
        overlayToggleKioskButton.setText(
            if (parentModeUnlocked) R.string.activate_kid_launcher_button else R.string.deactivate_kid_launcher_button
        )
        overlayOpenSettingsButton.visibility = if (parentModeUnlocked) View.VISIBLE else View.GONE
        overlayGrantWriteSettingsButton.visibility = if (Settings.System.canWrite(this)) View.GONE else View.VISIBLE
        scheduleEnabledSwitch.isChecked = SchoolModeStore.isScheduleEnabled(this)
        scheduleStartTimeButton.text =
            getString(R.string.school_mode_schedule_start_format, formatMinutes(SchoolModeStore.getStartMinutes(this)))
        scheduleEndTimeButton.text =
            getString(R.string.school_mode_schedule_end_format, formatMinutes(SchoolModeStore.getEndMinutes(this)))
        parentOverlay.visibility = View.VISIBLE
    }

    private fun onToggleKioskClicked() {
        if (parentModeUnlocked) {
            reactivateKidsMode()
        } else {
            deactivateKidLauncher()
        }
        showParentOverlay()
    }

    private fun deactivateKidLauncher() {
        parentModeUnlocked = true
        if (isInLockTaskMode()) {
            stopLockTask()
        }
    }

    private fun reactivateKidsMode() {
        parentModeUnlocked = false
        setupKioskIfDeviceOwner()
    }

    private fun showChangePinDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val newPinInput = EditText(this).apply {
            hint = getString(R.string.change_pin_new_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val confirmPinInput = EditText(this).apply {
            hint = getString(R.string.change_pin_confirm_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        layout.addView(newPinInput)
        layout.addView(confirmPinInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.change_pin_title)
            .setView(layout)
            .setPositiveButton(R.string.parent_pin_confirm) { _, _ ->
                val newPin = newPinInput.text.toString()
                val confirmPin = confirmPinInput.text.toString()
                if (newPin.isNotEmpty() && newPin == confirmPin) {
                    ParentPinStore.setPin(this, newPin)
                    Toast.makeText(this, R.string.change_pin_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.change_pin_mismatch, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupKioskIfDeviceOwner() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Log.w(TAG, "App ist kein Device Owner - Lock Task wird nicht gestartet")
            statusText.visibility = View.VISIBLE
            statusText.setText(R.string.launcher_no_device_owner)
            return
        }

        statusText.visibility = View.GONE
        dpm.setLockTaskPackages(admin, ALLOWED_PACKAGES)

        // Erlaubt der Statusleiste, während Lock Task angezeigt zu werden (z.B. per Swipe-Geste
        // transient) - siehe hideStatusBar() für die Standardanzeige im Vollbild.
        // Bewusst OHNE LOCK_TASK_FEATURE_NOTIFICATIONS: Android erlaubt das nur zusammen mit
        // LOCK_TASK_FEATURE_HOME (sonst IllegalArgumentException, Absturz bei jedem onResume - siehe
        // Git-Historie) und würde ohnehin alle Benachrichtigungen freischalten, nicht nur Anrufe.
        // Eingehende Anrufe zeigt stattdessen CallRingingReceiver über eine eigene Annehmen/Ablehnen-
        // Oberfläche in dieser Activity an (siehe showIncomingCallOverlay()) - Heads-up-Notifications
        // des System-Dialers werden bei fokussierter Lock-Task-App ohnehin unterdrückt.
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)

        // Kein echter Dialer in der Whitelist -> Launcher löst Anrufe selbst aus (Kurzwahl),
        // daher muss CALL_PHONE ohne Laufzeit-Dialog gewährt sein.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.CALL_PHONE,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // Für CallRingingReceiver: Empfang von ACTION_PHONE_STATE_CHANGED erfordert READ_PHONE_STATE.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.READ_PHONE_STATE,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // Für acceptRingingCall() in der eigenen Annehmen/Ablehnen-Oberfläche.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.ANSWER_PHONE_CALLS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // Seit Android 10 liefert der PHONE_STATE-Broadcast EXTRA_INCOMING_NUMBER nur mit
        // READ_CALL_LOG - ohne das bliebe die Anruferanzeige ohne Name/Foto/Nummer.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.READ_CALL_LOG,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // ACTION_PICK gewährt nur einen kurzzeitigen Lesezugriff auf die Kontaktfoto-URI (nur für den
        // Moment der Auswahl in SpeedDialManagerActivity). Damit Kontaktfotos auch später geladen
        // werden können, braucht die App dauerhaft READ_CONTACTS.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.READ_CONTACTS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // Für die letzte SMS der Kurzwahl-Kontakte neben dem Foto.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.READ_SMS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        // Für SmsReceivedReceiver: Live-Update der Anzeige bei neu eintreffender SMS.
        dpm.setPermissionGrantState(
            admin,
            packageName,
            Manifest.permission.RECEIVE_SMS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )

        applyRingerSettings(this, SchoolModeStore.isEnabled(this))

        if (!isInLockTaskMode()) {
            startLockTask()
        }
    }

    private fun isInLockTaskMode(): Boolean {
        val activityManager = getSystemService(ActivityManager::class.java)
        return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    // Rendert die Kind-Ansicht als Liste: Apps aus der Whitelist und Kurzwahl-Kontakte als
    // einheitliche Text-Zeilen.
    private fun renderList() {
        itemListContainer.removeAllViews()
        renderAppItems()
        renderContactItems()
    }

    private fun renderAppItems() {
        val pm = packageManager

        for (allowedPackage in ALLOWED_PACKAGES) {
            // Eigener Launcher braucht keine Zeile; Dialer ist nur für die In-Call-UI der Kurzwahl
            // freigegeben und soll nicht als direkt startbare App erscheinen (siehe CLAUDE.md).
            if (allowedPackage == packageName || allowedPackage in HIDDEN_FROM_LIST) continue

            val launchIntent = pm.getLaunchIntentForPackage(allowedPackage) ?: continue
            val appInfo = try {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(allowedPackage, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }

            addListRow(pm.getApplicationLabel(appInfo).toString()) { startActivity(launchIntent) }
        }
    }

    private fun renderContactItems() {
        SpeedDialStore.load(this).forEach { contact ->
            addListRow(contact.label) {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.number}")))
            }
        }
    }

    private fun addListRow(label: String, onClick: () -> Unit) {
        val rowView = layoutInflater.inflate(R.layout.item_list_row, itemListContainer, false)
        rowView.findViewById<TextView>(R.id.rowLabel).text = label
        rowView.findViewById<View>(R.id.rowContent).setOnClickListener { onClick() }
        itemListContainer.addView(rowView)
    }
}
