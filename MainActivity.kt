package com.exemplo.pausa

import android.Manifest
import android.app.Activity
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val BUILD = "diag-2026-11-08-a"
    private lateinit var painel: TextView

    private fun status(): String {
        val declarado = try {
            packageManager.getServiceInfo(ComponentName(this, ChromeGuardService::class.java), 0)
            true
        } catch (e: Exception) {
            false
        }
        val ativos = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val ligado = ativos.contains(packageName)
        return "Build: " + BUILD +
            "\nServiço no manifest: " + (if (declarado) "SIM" else "NÃO") +
            "\nAcessibilidade ligada: " + (if (ligado) "SIM" else "NÃO") +
            "\nServiço conectado: " + (if (ChromeGuardService.conectado) "SIM" else "NÃO") +
            "\nEventos do Chrome: " + ChromeGuardService.eventosChrome +
            "\nÚltima leitura: " + ChromeGuardService.diag
    }

    override fun onResume() {
        super.onResume()
        painel.text = status()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titulo = TextView(this).apply {
            text = "Pausa — proteção contra apostas"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val b1 = Button(this).apply {
            text = "1. Permitir acesso ao uso de apps"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        val b2 = Button(this).apply {
            text = "2. Permitir exibir sobre outros apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        val b3 = Button(this).apply {
            text = "3. Ativar proteção"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
                startForegroundService(Intent(this@MainActivity, BloqueioService::class.java))
            }
        }
        val tipoPin = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val campoAtual = EditText(this).apply {
            hint = "PIN atual (se já definido)"
            inputType = tipoPin
        }
        val campoNovo = EditText(this).apply {
            hint = "Novo PIN (4 a 6 dígitos)"
            inputType = tipoPin
        }
        val b4 = Button(this).apply {
            text = "Salvar PIN do contato de confiança"
            setOnClickListener {
                val atual = campoAtual.text.toString()
                val novo = campoNovo.text.toString()
                if (Pin.definido(this@MainActivity) && !Pin.confere(this@MainActivity, atual)) {
                    Toast.makeText(this@MainActivity, "PIN atual incorreto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (novo.length !in 4..6) {
                    Toast.makeText(this@MainActivity, "Use de 4 a 6 dígitos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Pin.salvar(this@MainActivity, novo)
                campoAtual.setText("")
                campoNovo.setText("")
                Toast.makeText(this@MainActivity, "PIN salvo", Toast.LENGTH_SHORT).show()
            }
        }
        val b5 = Button(this).apply {
            text = "Desativar proteção (pede PIN)"
            setOnClickListener {
                if (Pin.definido(this@MainActivity) &&
                    !Pin.confere(this@MainActivity, campoAtual.text.toString())
                ) {
                    Toast.makeText(this@MainActivity, "PIN incorreto", Toast.LENGTH_SHORT).show()
                } else {
                    stopService(Intent(this@MainActivity, BloqueioService::class.java))
                }
            }
        }

        val saida = TextView(this).apply {
            textSize = 16f
            setPadding(0, 16, 0, 16)
            setTextIsSelectable(true)
        }
        val b6 = Button(this).apply {
            text = "Descobrir app aberto"
            setOnClickListener {
                val ctx = this@MainActivity
                val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val agora = System.currentTimeMillis()
                val eventos = usm.queryEvents(agora - 10 * 60_000L, agora)
                val e = UsageEvents.Event()
                val lista = mutableListOf<String>()
                while (eventos.hasNextEvent()) {
                    eventos.getNextEvent(e)
                    if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lista.remove(e.packageName)
                        lista.add(e.packageName)
                    }
                }
                val home = ctx.packageManager
                    .resolveActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
                    )?.activityInfo?.packageName
                val candidatos = lista.reversed()
                    .filter { it != ctx.packageName && it != home && !it.startsWith("com.android.systemui") }
                    .take(3)
                if (candidatos.isEmpty()) {
                    saida.text = "Nada encontrado. Confirme o passo 1 (acesso ao uso), abra o app da bet, volte aqui e toque de novo."
                } else {
                    saida.text = "Apps recentes (mais novo primeiro):\n" + candidatos.joinToString("\n")
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("pacote", candidatos[0]))
                    Toast.makeText(ctx, "Copiado: " + candidatos[0], Toast.LENGTH_LONG).show()
                }
            }
        }

        painel = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
            setTextIsSelectable(true)
        }
        val b7 = Button(this).apply {
            text = "Abrir configurações de Acessibilidade"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val b8 = Button(this).apply {
            text = "Atualizar status"
            setOnClickListener {
                painel.text = status()
            }
        }

        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(titulo)
            addView(painel)
            addView(b8)
            addView(b7)
            addView(b1)
            addView(b2)
            addView(b3)
            addView(campoAtual)
            addView(campoNovo)
            addView(b4)
            addView(b5)
            addView(b6)
            addView(saida)
        }
        setContentView(ScrollView(this).apply { addView(coluna) })
    }
}
