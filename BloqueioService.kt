package com.exemplo.pausa

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class BloqueioService : Service() {

    companion object {
        // Troque pelos pacotes reais das bets que seu amigo usa.
        // Para achar: na página do app na Play Store, o pacote é o valor de "?id=" na URL.
        val BETS = setOf(
            "com.exemplo.bet1",
            "com.exemplo.bet2"
        )
        const val ESPERA_MS = 20 * 60_000L      // espera antes de poder liberar
        const val LIBERACAO_MS = 10 * 60_000L   // tempo liberado depois da espera
        const val MENSAGEM = "Respire fundo. Esse dinheiro é para o seu futuro. Não jogue hoje! ❤️"
        const val CANAL = "pausa_canal"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var timer: CountDownTimer? = null
    private var liberadoAte = 0L
    private var pacoteAtual = ""

    private val loop = object : Runnable {
        override fun run() {
            verificar()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        iniciarForeground()
        handler.post(loop)
    }

    private fun iniciarForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Proteção ativa", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, CANAL)
            .setContentTitle("Proteção ativa")
            .setContentText("Monitorando apps de apostas")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
    }

    private fun appEmPrimeiroPlano(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val agora = System.currentTimeMillis()
        val eventos = usm.queryEvents(agora - 10_000, agora)
        val e = UsageEvents.Event()
        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> pacoteAtual = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    if (e.packageName == pacoteAtual) pacoteAtual = ""
            }
        }
        return pacoteAtual
    }

    private fun verificar() {
        val pkg = appEmPrimeiroPlano()
        val agora = System.currentTimeMillis()
        if (pkg in BETS && agora > liberadoAte && overlay == null) {
            mostrarOverlay()
        } else if (pkg.isNotEmpty() && pkg !in BETS && overlay != null) {
            removerOverlay()
        }
    }

    private fun mostrarOverlay() {
        val texto = TextView(this).apply {
            text = MENSAGEM
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 48)
        }
        val contagem = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 32f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        val voltar = Button(this).apply {
            text = "Voltar para o início"
            setOnClickListener {
                removerOverlay()
                startActivity(
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }
        }
        val liberar = Button(this).apply {
            text = "Liberar (aguarde)"
            isEnabled = false
            setOnClickListener {
                liberadoAte = System.currentTimeMillis() + LIBERACAO_MS
                removerOverlay()
            }
        }
        val campoPin = EditText(this).apply {
            hint = "PIN do contato"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            visibility = View.GONE
        }
        val confirmarPin = Button(this).apply {
            text = "Confirmar PIN"
            visibility = View.GONE
            setOnClickListener {
                if (Pin.confere(this@BloqueioService, campoPin.text.toString())) {
                    liberadoAte = System.currentTimeMillis() + LIBERACAO_MS
                    removerOverlay()
                } else {
                    campoPin.setText("")
                    campoPin.error = "PIN incorreto"
                }
            }
        }
        val botaoPin = Button(this).apply {
            text = "Liberar com PIN do contato"
            visibility = if (Pin.definido(this@BloqueioService)) View.VISIBLE else View.GONE
            setOnClickListener {
                campoPin.visibility = View.VISIBLE
                confirmarPin.visibility = View.VISIBLE
                visibility = View.GONE
            }
        }
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2101010"))
            addView(texto)
            addView(contagem)
            addView(voltar)
            addView(liberar)
            addView(botaoPin)
            addView(campoPin)
            addView(confirmarPin)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(raiz, params)
        overlay = raiz

        timer = object : CountDownTimer(ESPERA_MS, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                contagem.text = "%02d:%02d".format(s / 60, s % 60)
            }
            override fun onFinish() {
                contagem.text = "00:00"
                liberar.isEnabled = true
                liberar.text = "Liberar por 10 minutos"
            }
        }.start()
    }

    private fun removerOverlay() {
        timer?.cancel()
        timer = null
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        removerOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
