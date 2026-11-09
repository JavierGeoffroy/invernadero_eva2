package com.example.invernadero

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.hide()
        val p = Prefs.get(this)
        val etPin = findViewById<EditText>(R.id.etPin)
        val etPass = findViewById<EditText>(R.id.etPass)
        val lblPass = findViewById<TextView>(R.id.lblPass)
        val btn = findViewById<Button>(R.id.btnLogin)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val msg = findViewById<TextView>(R.id.tvMsg)
        val first = p.getString("pin", null) == null

        if (first) {
            msg.text = "Primera vez: crea un PIN (mín. 4 dígitos) y una clave compartida (mín. 8 caracteres). " +
                    "Debes usar la MISMA clave compartida en los dos celulares."
            etPin.hint = "Crea tu PIN"
            btnReset.visibility = View.GONE
        } else {
            msg.text = "Ingresa tu PIN para continuar."
            etPass.visibility = View.GONE
            lblPass.visibility = View.GONE
        }

        btn.setOnClickListener {
            val pin = etPin.text.toString()
            if (first) {
                val pass = etPass.text.toString()
                if (pin.length < 4 || pass.length < 8) { toast("PIN o clave demasiado cortos"); return@setOnClickListener }
                p.edit().putString("pin", Prefs.sha256(pin)).putString("pass", pass).apply()
                enter(pass)
                return@setOnClickListener
            }
            // Control de acceso: el bloqueo se guarda en almacenamiento cifrado, así que reiniciar la app no lo evita
            val wait = p.getLong("lock", 0L) - System.currentTimeMillis()
            if (wait > 0) { toast("Acceso bloqueado. Espera ${wait / 1000 + 1} s"); return@setOnClickListener }
            if (Prefs.sha256(pin) == p.getString("pin", "")) {
                p.edit().putInt("fails", 0).apply()
                enter(p.getString("pass", "") ?: "")
            } else {
                val fails = p.getInt("fails", 0) + 1
                if (fails >= 3) {
                    p.edit().putInt("fails", 0).putLong("lock", System.currentTimeMillis() + 30_000).apply()
                    toast("3 intentos fallidos: acceso bloqueado 30 s")
                } else {
                    p.edit().putInt("fails", fails).apply()
                    toast("PIN incorrecto ($fails/3)")
                }
                etPin.text.clear()
            }
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Restablecer PIN y clave")
                .setMessage("Se borrarán el PIN y la clave compartida de este celular. Tendrás que crearlos de nuevo y usar la misma clave en el otro celular.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Borrar") { _, _ -> p.edit().clear().apply(); recreate() }
                .show()
        }
    }

    private fun enter(pass: String) {
        Session.key = Crypto.keyFrom(pass)
        startActivity(Intent(this, ModeActivity::class.java))
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
