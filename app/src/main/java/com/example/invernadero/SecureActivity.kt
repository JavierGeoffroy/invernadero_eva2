package com.example.invernadero

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Base de las pantallas protegidas:
 *  - exige una sesión iniciada (si no, vuelve al login)
 *  - cierra la sesión tras 60 s en segundo plano (bloqueo automático)
 *  - el botón "volver" de la barra superior regresa a la pantalla anterior
 */
open class SecureActivity : AppCompatActivity() {
    protected open val autoLock = true

    override fun onStart() {
        super.onStart()
        val away = Session.lastStop != 0L && System.currentTimeMillis() - Session.lastStop > 60_000
        if (Session.key == null || (autoLock && away)) { logout(); return }
        Session.lastStop = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) Session.lastStop = System.currentTimeMillis()
    }

    fun logout() {
        Session.key = null
        Session.lastStop = 0L
        startActivity(Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
