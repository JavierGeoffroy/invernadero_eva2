package com.example.invernadero

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CELULAR A (nodo del invernadero):
 *  Arduino --Bluetooth (HC-05)--> este celular --WiFi (socket TCP cifrado)--> Celular B (monitor)
 * No se bloquea solo en segundo plano para no cortar el monitoreo.
 */
class NodeActivity : SecureActivity() {
    override val autoLock = false

    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val PORT = 5000
    private val ALLOWED = setOf("FAN:1", "FAN:0")                       // lista blanca de comandos
    private val READING = Regex("^T:-?\\d+(\\.\\d+)?;H:\\d+(\\.\\d+)?$")  // formato válido de lectura

    private val clients = CopyOnWriteArrayList<PrintWriter>()
    private var btSocket: BluetoothSocket? = null
    private var btOut: OutputStream? = null
    private var server: ServerSocket? = null
    private val key get() = Session.key!!

    private lateinit var tvIp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvData: TextView
    private lateinit var tvClients: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node)
        title = "Nodo del invernadero"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (Session.key == null) return   // SecureActivity.onStart enviará al login
        tvIp = findViewById(R.id.tvIp); tvStatus = findViewById(R.id.tvStatus)
        tvData = findViewById(R.id.tvData); tvClients = findViewById(R.id.tvClients)
        tvIp.text = "${localIp()} : $PORT"
        findViewById<Button>(R.id.btnBt).setOnClickListener { pickDevice() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("ip", localIp()))
            toast("IP copiada")
        }
        if (!hasBt()) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        startServer()
    }

    private fun hasBt() = Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun pickDevice() {
        if (!hasBt()) { requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1); return }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { toast("Activa el Bluetooth"); return }
        val devs = adapter.bondedDevices.toList()
        if (devs.isEmpty()) { toast("Vincula antes el HC-05 en Ajustes > Bluetooth (PIN 1234 o 0000)"); return }
        AlertDialog.Builder(this).setTitle("Elige el módulo del Arduino")
            .setItems(devs.map { "${it.name}\n${it.address}" }.toTypedArray()) { _, i -> connectBt(devs[i]) }
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun connectBt(d: BluetoothDevice) {
        Thread {
            try {
                val s = d.createRfcommSocketToServiceRecord(SPP); s.connect()
                btSocket = s; btOut = s.outputStream
                ui { tvStatus.text = "Arduino conectado (${d.name})" }
                val r = BufferedReader(InputStreamReader(s.inputStream))
                while (true) { handleLine((r.readLine() ?: break).trim()) }
                ui { tvStatus.text = "Arduino desconectado" }
            } catch (e: Exception) {
                ui { tvStatus.text = "Error Bluetooth: ${e.message}" }
            }
        }.start()
    }

    private fun handleLine(line: String) {
        if (!READING.matches(line)) return           // se descarta cualquier dato con formato inesperado
        ui { tvData.text = line }
        val enc = Crypto.encrypt(key, line)
        clients.forEach { if (it.checkError()) clients.remove(it) else it.println(enc) }
    }

    private fun startServer() {
        Thread {
            try {
                server = ServerSocket(PORT)
                while (true) { val c = server!!.accept(); Thread { serve(c) }.start() }
            } catch (e: Exception) { /* servidor cerrado */ }
        }.start()
    }

    private fun serve(c: Socket) {
        val w = PrintWriter(c.getOutputStream(), true)
        clients.add(w); ui { tvClients.text = "Monitores conectados: ${clients.size}" }
        try {
            val r = BufferedReader(InputStreamReader(c.getInputStream()))
            while (true) {
                val l = r.readLine() ?: break
                val cmd = Crypto.decrypt(key, l) ?: continue   // mensaje no auténtico: se ignora
                if (cmd in ALLOWED) { btOut?.write("$cmd\n".toByteArray()); btOut?.flush() }
            }
        } catch (e: Exception) { }
        finally { clients.remove(w); c.close(); ui { tvClients.text = "Monitores conectados: ${clients.size}" } }
    }

    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress ?: "sin WiFi"
    } catch (e: Exception) { "sin WiFi" }

    private fun ui(f: () -> Unit) = runOnUiThread(f)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        try { server?.close(); btSocket?.close() } catch (e: Exception) { }
    }
}
