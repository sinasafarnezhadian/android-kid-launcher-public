package net.sinas.kidlauncher

import android.content.Context
import android.net.Uri
import java.io.File

object FeaturedPhotoStore {
    private const val FILE_NAME = "featured_photo.jpg"

    fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasPhoto(context: Context): Boolean = getFile(context).exists()

    // Kopiert die Bilddaten sofort in den internen Speicher, statt nur die URI zu merken - sonst
    // verfällt der (nur kurzzeitige) Lesezugriff des Bild-Pickers, genau wie beim Kontaktfoto-Bug.
    fun savePhoto(context: Context, sourceUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                getFile(context).outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            true
        } catch (e: Exception) {
            false
        }
    }
}
