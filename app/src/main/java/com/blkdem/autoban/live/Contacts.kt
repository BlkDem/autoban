package com.blkdem.autoban.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/** Имя номера в контактах телефона. Нужно разрешение «Контакты»; без него — null. */
object Contacts {
    private const val TAG = "Autoban"

    fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun name(context: Context, number: String?): String? {
        if (number.isNullOrBlank() || !granted(context)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "contact lookup failed: $e")
            null
        }
    }
}
