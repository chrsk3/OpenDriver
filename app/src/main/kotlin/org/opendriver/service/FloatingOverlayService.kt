package org.opendriver.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.opendriver.core.model.BubbleSize
import org.opendriver.core.model.ViabilityResult
import org.opendriver.core.model.ViabilityStatus
import org.opendriver.data.PreferencesRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Serviço que exibe e gerencia a bolha flutuante sobre outros aplicativos.
 *
 * Dois elementos distintos compõem o overlay:
 *  - O **botão flutuante** (trigger circular persistente) cuja opacidade é configurável
 *    e que abre/fecha o card de resultado.
 *  - O **card de resultado** (estilo "crachá": fundo branco, borda colorida pela
 *    viabilidade e três métricas), sempre 100% opaco, com todas as dimensões
 *    escaladas pelo multiplicador do [BubbleSize] escolhido.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var cardView: View? = null
    private var triggerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density).toInt()

    private val localeBr: Locale = Locale.forLanguageTag("pt-BR")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        logLine("service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        logLine("onStartCommand action=$action hasResult=${currentResult != null}")

        if (action == ACTION_HIDE) {
            removeEverything()
            stopSelf()
            return START_NOT_STICKY
        }

        // O botão flutuante fica visível enquanto o Copiloto estiver ligado.
        showTrigger()
        currentResult?.let { res -> showCard(res) }

        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val channelId = "opendriver_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OpenDriver Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação de serviço em segundo plano da bolha flutuante"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OpenDriver Ativo")
            .setContentText("Calculando viabilidade de corridas em segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    // ── Botão flutuante (trigger) ────────────────────────────────────────────

    private fun showTrigger() {
        val wm = windowManager ?: return
        if (triggerView != null) {
            triggerView?.alpha = PreferencesRepository(this).getFloatingButtonOpacity() / 100f
            return
        }

        val opacity = PreferencesRepository(this).getFloatingButtonOpacity()
        val size = dp(60f)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16f)
            y = dp(120f)
        }

        val trigger = TextView(this).apply {
            text = "🚗" // 🚗
            textSize = 26f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
                setStroke(dp(2f), Color.parseColor("#B0FFFFFF"))
            }
            alpha = opacity / 100f
        }

        attachTriggerGestures(trigger, params, wm)
        triggerView = trigger

        try {
            wm.addView(trigger, params)
            logLine("trigger addView ok")
        } catch (e: Exception) {
            triggerView = null
            logLine("trigger addView FAIL: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTriggerGestures(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downTime = 0L
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downTime = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) moved = true
                    // gravity = TOP|END: o eixo X é medido a partir da borda direita,
                    // então o deslocamento horizontal tem sinal invertido em relação ao dedo.
                    params.x = initialX - dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        val longPress = System.currentTimeMillis() - downTime >= 550
                        if (longPress) {
                            removeEverything()
                            stopSelf()
                        } else {
                            // Clique no botão flutuante abre o app do OpenDriver.
                            openApp()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openApp() {
        try {
            val intent = Intent(this, org.opendriver.ui.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            logLine("trigger tap -> open MainActivity")
        } catch (e: Exception) {
            logLine("openApp FAIL: ${e.message}")
        }
    }

    // ── Card de resultado ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun showCard(res: ViabilityResult) {
        val wm = windowManager ?: return
        removeCard()

        val prefs = PreferencesRepository(this)
        val hGravity = when (prefs.getCardPosition()) {
            "Centro" -> Gravity.CENTER_HORIZONTAL
            "Direita" -> Gravity.END
            else -> Gravity.START
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or hGravity
            x = dp(16f)
            y = dp(120f)
        }

        val view = createCardView(res, params, wm, prefs)
        cardView = view

        if (prefs.getTextNotificationEnabled()) {
            postTextNotification(res)
        }

        try {
            wm.addView(view, params)
            logLine("card addView ok")
        } catch (e: Exception) {
            cardView = null
            logLine("card addView FAIL: ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        val durationMs = PreferencesRepository(this).getCardDurationSeconds().coerceIn(2, 60) * 1000L
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = Runnable { removeCard() }
        handler.postDelayed(autoDismissRunnable!!, durationMs)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        res: ViabilityResult,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        prefs: PreferencesRepository
    ): View {
        // Um único multiplicador deriva as dimensões do frame; a fonte tem escala própria.
        val s = prefs.getBubbleSize().scale
        val fs = prefs.getCardFontSize() / 15f

        val dark = prefs.getCardTheme() == "Escuro"
        val textColor = if (dark) Color.WHITE else Color.parseColor("#111111")
        val labelColor = Color.parseColor("#9E9E9E")
        val bgColor = if (dark) Color.parseColor("#1B1B1B") else Color.WHITE

        // Semáforo: verde (bate metas) / amarelo (regular) / vermelho (prejuízo).
        // A paleta troca o tom do "verde" quando a corrida é boa/excelente.
        val accent = when (res.status) {
            ViabilityStatus.REGULAR -> Color.parseColor("#F9A825")
            ViabilityStatus.UNPROFITABLE -> Color.parseColor("#E53935")
            else -> paletteColorInt(prefs.getCardPalette())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f * s), dp(14f * s), dp(18f * s), dp(14f * s))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f * s).toFloat()
                setColor(bgColor)
                setStroke(dp(3f * s).coerceAtLeast(2), accent)
            }
        }

        // ── Métricas selecionadas (na ordem configurada) ─────────────────────
        val metricIds = prefs.getCardMetrics().ifEmpty { listOf("Ganho/km", "Ganho/h", "Nota") }
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        metrics.layoutParams = LinearLayout.LayoutParams(
            (300 * density * s).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        metricIds.forEach { id ->
            val (label, value) = metricValue(id, res)
            metrics.addView(metricColumn(label, value, accent, textColor, labelColor, s, fs))
        }
        root.addView(metrics)

        // ── Rodapé: badge da plataforma + tempo · distância ─────────────────
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        footer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10f * s) }

        footer.addView(platformBadge(res.offer.platform.displayName, s, fs))

        val tokens = mutableListOf(formatDuration(res.offer.totalDurationMinutes), formatKm(res.offer.totalDistanceKm))
        if (res.offer.pickupDistanceKm > 0.0) {
            tokens.add("Busca ${formatKm(res.offer.pickupDistanceKm)} km")
        }
        val footerText = TextView(this).apply {
            text = tokens.joinToString(" · ")
            setTextColor(textColor)
            textSize = 15f * s * fs
            typeface = Typeface.DEFAULT_BOLD
        }
        footer.addView(footerText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(8f * s) })

        root.addView(footer)

        attachCardDrag(root, params, wm)
        return root
    }

    /** Coluna de métrica: rótulo pequeno em cima e valor grande com barra colorida à esquerda. */
    private fun metricColumn(
        label: String,
        value: String,
        accent: Int,
        textColor: Int,
        labelColor: Int,
        s: Float,
        fs: Float
    ): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(labelColor)
            textSize = 12f * s * fs
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val bar = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5f * s), dp(26f * s))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3f * s).toFloat()
                setColor(accent)
            }
        }

        val valueView = TextView(this).apply {
            text = value
            setTextColor(textColor)
            textSize = 22f * s * fs
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(6f * s) }
        }

        row.addView(bar)
        row.addView(valueView)
        column.addView(labelView)
        column.addView(row)
        return column
    }

    private fun platformBadge(name: String, s: Float, fs: Float): View {
        return TextView(this).apply {
            text = name.uppercase(localeBr)
            setTextColor(Color.WHITE)
            textSize = 11f * s * fs
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(6f * s), dp(3f * s), dp(6f * s), dp(3f * s))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4f * s).toFloat()
                setColor(Color.parseColor("#1A1A1A"))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachCardDrag(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        wm.updateViewLayout(view, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun statusColor(status: ViabilityStatus): Int = when (status) {
        ViabilityStatus.EXCELLENT -> Color.parseColor("#00C853")
        ViabilityStatus.GOOD -> Color.parseColor("#43A047")
        ViabilityStatus.REGULAR -> Color.parseColor("#F9A825")
        ViabilityStatus.UNPROFITABLE -> Color.parseColor("#E53935")
    }

    private fun format2(value: Double): String = String.format(localeBr, "%.2f", value)
    private fun formatKm(value: Double): String = String.format(localeBr, "%.1f", value)

    private fun formatDuration(minutes: Double): String {
        val total = minutes.toInt()
        return "%dh%02dm".format(total / 60, total % 60)
    }

    // ── Remoção / ciclo de vida ──────────────────────────────────────────────

    private fun metricValue(id: String, res: ViabilityResult): Pair<String, String> = when (id) {
        "Ganho/km" -> "$/Km" to format2(res.grossPerKm)
        "Ganho/h" -> "$/Hora" to format2(res.grossHourlyRate)
        "Nota" -> "Nota" to (res.offer.passengerRating?.let { format2(it) } ?: "--")
        "Lucro" -> "Lucro" to format2(res.netProfit)
        else -> id to "--"
    }

    private fun paletteColorInt(name: String): Int = when (name) {
        "Azul" -> Color.parseColor("#40C4FF")
        "Roxo" -> Color.parseColor("#B388FF")
        else -> Color.parseColor("#00C853")
    }

    private fun postTextNotification(res: ViabilityResult) {
        try {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            val text = "%s • Lucro R$ %.2f • R$ %.2f/km • R$ %.1f/h".format(
                Locale.US, res.offer.platform.displayName, res.netProfit, res.netPerKm, res.netHourlyRate
            )
            val notification = NotificationCompat.Builder(this, "opendriver_overlay_channel")
                .setContentTitle("OpenDriver • ${res.status.label}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            mgr.notify(2002, notification)
        } catch (_: Exception) {}
    }

    private fun logLine(msg: String) {
        Log.i("OpenDriver", "[overlay] $msg")
        logToFile(this, msg)
    }

    private fun removeCard() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        cardView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            cardView = null
        }
    }

    private fun removeEverything() {
        removeCard()
        triggerView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            triggerView = null
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

    override fun onDestroy() {
        removeEverything()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "org.opendriver.SHOW_OVERLAY"
        const val ACTION_HIDE = "org.opendriver.HIDE_OVERLAY"

        private var currentResult: ViabilityResult? = null

        fun showResult(context: Context, result: ViabilityResult) {
            currentResult = result
            logToFile(context, "showResult: R$ %.2f • %.1f km • %s".format(
                Locale.US, result.offer.grossFare, result.offer.totalDistanceKm, result.status.label
            ))
            startService(context)
        }

        /** Mostra apenas o botão flutuante (sem card), usado quando o Copiloto é ligado. */
        fun enable(context: Context) {
            startService(context)
        }

        private fun startService(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ pode bloquear start de FGS em background; tenta o start normal.
                logToFile(context, "startForegroundService FAIL: ${e.javaClass.simpleName}: ${e.message}")
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    logToFile(context, "startService FAIL: ${e2.javaClass.simpleName}: ${e2.message}")
                }
            }
        }

        private val logExecutor = Executors.newSingleThreadExecutor()

        // Escreve em background: nunca bloquear a main thread (mesma do overlay).
        private fun logToFile(context: Context, msg: String) {
            logExecutor.execute {
                try {
                    val dir = context.getExternalMediaDirs()?.firstOrNull() ?: return@execute
                    val file = File(dir, "overlay_log.txt")
                    if (file.exists() && file.length() > 30_000) {
                        file.writeText(file.readText().takeLast(15_000))
                    }
                    FileOutputStream(file, true).use {
                        it.write("${System.currentTimeMillis()} $msg\n".toByteArray())
                    }
                } catch (_: Exception) {}
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }
}
