package com.example.invernadero

import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

/** CELULAR B (monitor remoto): recibe lecturas cifradas por WiFi, muestra alertas y controla el ventilador. */
class MonitorActivity : SecureActivity() {
    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private val history = ArrayList<String>()
    private var lastAlert: Boolean? = null
    private val key get() = Session.key!!

    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHistory: TextView
    private lateinit var etMaxT: EditText
    private lateinit var etMaxH: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        title = "Monitor remoto"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tvTemp = findViewById(R.id.tvTemp); tvHum = findViewById(R.id.tvHum)
        tvAlert = findViewById(R.id.tvAlert); tvStatus = findViewById(R.id.tvStatus)
        tvHistory = findViewById(R.id.tvHistory)
        etMaxT = findViewById(R.id.etMaxT); etMaxH = findViewById(R.id.etMaxH)
        val etIp = findViewById<EditText>(R.id.etIp)

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (!Patterns.IP_ADDRESS.matcher(ip).matches()) { toast("IP inválida"); return@setOnClickListener }
            connect(ip)
        }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            try { socket?.close() } catch (e: Exception) { }
            tvStatus.text = "Desconectado"
        }
        findViewById<SwitchCompat>(R.id.swFan).setOnCheckedChangeListener { _, on -> send(if (on) "FAN:1" else "FAN:0") }
    }

    private fun connect(ip: String) {
        tvStatus.text = "Conectando a $ip..."
        Thread {
            try {
                val s = Socket(); s.connect(InetSocketAddress(ip, 5000), 5000)
                socket = s; out = PrintWriter(s.getOutputStream(), true)
                runOnUiThread { tvStatus.text = "Conectado a $ip" }
                val r = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = r.readLine() ?: break
                    val msg = Crypto.decrypt(key, line) ?: continue
                    runOnUiThread { show(msg) }
                }
                runOnUiThread { tvStatus.text = "Desconectado" }
            } catch (e: Exception) {
                runOnUiThread { tvStatus.text = "Error: ${e.message}" }
            }
        }.start()
    }

    private fun show(m: String) {
        val parts = m.split(";")
        if (parts.size < 2) return
        val t = parts[0].removePrefix("T:").toFloatOrNull() ?: return
        val h = parts[1].removePrefix("H:").toFloatOrNull() ?: return
        tvTemp.text = "%.1f °C".format(t); tvHum.text = "%.1f %%".format(h)
        tvTemp.contentDescription = "Temperatura %.1f grados Celsius".format(t)
        tvHum.contentDescription = "Humedad %.1f por ciento".format(h)

        val maxT = etMaxT.text.toString().toFloatOrNull() ?: 30f
        val maxH = etMaxH.text.toString().toFloatOrNull() ?: 80f
        val alert = t > maxT || h > maxH
        if (alert != lastAlert) {   // solo se actualiza (y se anuncia a TalkBack) cuando cambia el estado
            lastAlert = alert
            tvAlert.text = if (alert) "⚠ ALERTA: valores sobre el umbral" else "✓ Valores normales"
            tvAlert.setBackgroundResource(if (alert) R.drawable.bg_alert_bad else R.drawable.bg_alert_ok)
            tvAlert.setTextColor(getColor(if (alert) R.color.alert_text else R.color.ok_text))
        }

        history.add(0, "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}   T=$t °C   H=$h %")
        if (history.size > 20) history.removeAt(history.size - 1)
        tvHistory.text = history.joinToString("\n")
    }

    private fun send(cmd: String) {
        Thread { out?.println(Crypto.encrypt(key, cmd)) }.start()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    override fun onDestroy() { super.onDestroy(); try { socket?.close() } catch (e: Exception) { } }
}
