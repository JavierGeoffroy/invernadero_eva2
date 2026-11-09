package com.example.invernadero

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/** Almacenamiento cifrado de credenciales (PIN con hash, clave compartida y bloqueo por intentos). */
object Prefs {
    fun get(c: Context): SharedPreferences {
        val mk = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            c, "secure_prefs", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Estado de la sesión actual: clave AES (solo en memoria) y momento en que la app pasó a segundo plano. */
object Session {
    var key: SecretKeySpec? = null
    var lastStop: Long = 0L
}
