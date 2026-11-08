package com.exemplo.pausa

import android.Manifest
import android.app.Activity
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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

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

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(titulo)
                addView(b1)
                addView(b2)
                addView(b3)
                addView(campoAtual)
                addView(campoNovo)
                addView(b4)
                addView(b5)
            }
        )
    }
}
