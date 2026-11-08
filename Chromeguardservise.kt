package com.exemplo.pausa

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

// Lista de sites confiáveis. Qualquer site fora dela (e fora dos que o contato
// de confiança liberar com PIN) abre a tela de espera.
object Confiaveis {
    private const val PREFS = "pausa"
    private const val KEY = "sites_confiaveis"

    val BASE = setOf(
        // Google e buscadores
        "google.com", "google.com.br", "gstatic.com", "googleapis.com",
        "googleusercontent.com", "youtube.com", "youtu.be", "gmail.com",
        "bing.com", "duckduckgo.com",
        // Redes sociais e mensagens
        "instagram.com", "facebook.com", "fb.com", "messenger.com",
        "whatsapp.com", "whatsapp.net", "wa.me", "tiktok.com", "x.com",
        "twitter.com", "t.co", "linkedin.com", "pinterest.com", "kwai.com",
        "telegram.org", "t.me", "discord.com", "reddit.com",
        // Dev e IA
        "github.com", "claude.ai", "anthropic.com", "chatgpt.com", "openai.com",
        // Serviços e bancos
        "microsoft.com", "live.com", "office.com", "apple.com", "icloud.com",
        "netflix.com", "spotify.com", "wikipedia.org",
        "nubank.com.br", "itau.com.br", "bradesco.com.br", "bb.com.br",
        "santander.com.br", "inter.co", "c6bank.com.br", "picpay.com",
        "pagbank.com.br", "sicredi.com.br", "sicoob.com.br", "mercadopago.com.br",
        "gov.br",
        // Compras, comida e transporte
        "amazon.com", "amazon.com.br", "mercadolivre.com.br", "mercadolibre.com",
        "shopee.com.br", "magazineluiza.com.br", "americanas.com.br",
        "olx.com.br", "ifood.com.br", "uber.com", "99app.com",
        // Notícias
        "globo.com", "uol.com.br", "terra.com.br"
    )

    private val SUFIXOS2 = setOf(
        "com.br", "org.br", "net.br", "gov.br", "edu.br", "co.uk", "com.pt"
    )

    fun dominio(host: String): String {
        val p = host.split(".")
        if (p.size <= 2) return host
        val ult2 = p.takeLast(2).joinToString(".")
        return if (ult2 in SUFIXOS2) p.takeLast(3).joinToString(".") else ult2
    }

    private fun salvos(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()) ?: emptySet()

    fun confiavel(ctx: Context, host: String): Boolean {
        if (dominio(host) in salvos(ctx)) return true
        return BASE.any { host == it || host.endsWith(".$it") }
    }

    fun adicionar(ctx: Context, host: String) {
        val novo = salvos(ctx).toMutableSet()
        novo.add(dominio(host))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, novo).apply()
    }
}

class ChromeGuardService : AccessibilityService() {

    companion object {
        const val CHROME = "com.android.chrome"
        const val ESPERA_MS = 20 * 60_000L      // espera antes de poder liberar
        const val LIBERACAO_MS = 10 * 60_000L   // tempo liberado depois da espera
        const val MENSAGEM = "Respire fundo. Esse dinheiro é para o seu futuro. Não jogue hoje! ❤️"
        private val IGNORAR = listOf("com.android.systemui", "inputmethod", "keyboard", "honeyboard")
        private val REGEX_HOST = Regex("^([a-z0-9-]+\\.)+[a-z]{2,}$")
    }

    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var timer: CountDownTimer? = null
    private val liberados = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(ev: AccessibilityEvent?) {
        val pkg = ev?.packageName?.toString() ?: return
        if (pkg == packageName || IGNORAR.any { pkg.contains(it) }) return
        if (pkg != CHROME) {
            // Saiu do Chrome: tira a tela de espera
            if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) esconder()
            return
        }
        verificarChrome()
    }

    private fun verificarChrome() {
        if (overlay != null) return
        val raiz = rootInActiveWindow ?: return
        if (raiz.packageName?.toString() != CHROME) return
        val host = lerHost(raiz) ?: return
        if (Confiaveis.confiavel(this, host)) return
        val dom = Confiaveis.dominio(host)
        if (System.currentTimeMillis() < (liberados[dom] ?: 0L)) return
        mostrar(host)
    }

    private fun lerHost(raiz: AccessibilityNodeInfo): String? {
        val porId = raiz.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        for (n in porId) {
            extrairHost(n.text?.toString())?.let { return it }
        }
        // Alternativa: outros campos de endereço da interface do Chrome
        val fila = ArrayDeque<AccessibilityNodeInfo>()
        fila.add(raiz)
        var vistos = 0
        while (fila.isNotEmpty() && vistos < 400) {
            val n = fila.removeFirst()
            vistos++
            val id = n.viewIdResourceName
            if (id != null && id.startsWith("com.android.chrome:id/") &&
                id.contains("url", ignoreCase = true)
            ) {
                extrairHost(n.text?.toString())?.let { return it }
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { fila.add(it) }
            }
        }
        return null
    }

    private fun extrairHost(bruto: String?): String? {
        var t = bruto?.trim()?.lowercase() ?: return null
        if (t.isEmpty() || t.contains(" ")) return null
        t = t.removePrefix("https://").removePrefix("http://")
        t = t.substringBefore("/").substringBefore("?").substringBefore("#").substringBefore(":")
        return if (REGEX_HOST.matches(t)) t else null
    }

    private fun mostrar(host: String) {
        val dom = Confiaveis.dominio(host)

        val texto = TextView(this).apply {
            text = MENSAGEM
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 24)
        }
        val site = TextView(this).apply {
            text = "Site novo: $host"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(48, 0, 48, 32)
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
                esconder()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        val liberar = Button(this).apply {
            text = "Liberar (aguarde)"
            isEnabled = false
            setOnClickListener {
                liberados[dom] = System.currentTimeMillis() + LIBERACAO_MS
                esconder()
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
                if (Pin.confere(this@ChromeGuardService, campoPin.text.toString())) {
                    Confiaveis.adicionar(this@ChromeGuardService, host)
                    esconder()
                } else {
                    campoPin.setText("")
                    campoPin.error = "PIN incorreto"
                }
            }
        }
        val botaoPin = Button(this).apply {
            text = "Confiar neste site (PIN do contato)"
            visibility = if (Pin.definido(this@ChromeGuardService)) View.VISIBLE else View.GONE
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
            addView(site)
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
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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

    private fun esconder() {
        timer?.cancel()
        timer = null
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        esconder()
        return super.onUnbind(intent)
    }
}
