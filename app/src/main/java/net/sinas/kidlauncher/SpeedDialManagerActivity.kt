package net.sinas.kidlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SpeedDialManagerActivity : Activity() {

    private lateinit var listContainer: LinearLayout
    private val contacts = mutableListOf<SpeedDialContact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_dial_manager)
        listContainer = findViewById(R.id.speedDialListContainer)

        findViewById<Button>(R.id.addManualButton).setOnClickListener { showManualAddDialog() }
        findViewById<Button>(R.id.pickContactButton).setOnClickListener { launchContactPicker() }

        contacts.addAll(SpeedDialStore.load(this))
        renderList()
    }

    private fun renderList() {
        listContainer.removeAllViews()
        contacts.forEachIndexed { index, contact ->
            val row = layoutInflater.inflate(R.layout.item_speed_dial, listContainer, false)
            row.findViewById<TextView>(R.id.speedDialLabel).text =
                getString(R.string.speed_dial_row_format, contact.label, contact.number)
            row.findViewById<Button>(R.id.removeButton).setOnClickListener {
                contacts.removeAt(index)
                persistAndRender()
            }
            listContainer.addView(row)
        }
    }

    private fun persistAndRender() {
        SpeedDialStore.save(this, contacts)
        renderList()
    }

    private fun showManualAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply { hint = getString(R.string.speed_dial_name_hint) }
        val numberInput = EditText(this).apply {
            hint = getString(R.string.speed_dial_number_hint)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        layout.addView(nameInput)
        layout.addView(numberInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.speed_dial_add_title)
            .setView(layout)
            .setPositiveButton(R.string.parent_pin_confirm) { _, _ ->
                val name = nameInput.text.toString().trim()
                val number = numberInput.text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    contacts.add(SpeedDialContact(name, number))
                    persistAndRender()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_CONTACT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_CONTACT || resultCode != RESULT_OK) return

        val contactUri = data?.data ?: return
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor: Cursor ->
            if (!cursor.moveToFirst()) return@use

            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val name = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
            val number = numberIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }
            val photoUri = photoIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }

            if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                contacts.add(SpeedDialContact(name, number, photoUri))
                persistAndRender()
            } else {
                Toast.makeText(this, R.string.speed_dial_pick_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_PICK_CONTACT = 1001
    }
}
