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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.opendriver.core.model.BubbleSize
import org.opendriver.core.model.ViabilityResult
import org.opendriver.core.model.ViabilityStatus
import org.opendriver.data.PreferencesRepository
import java.util.Locale
import kotlin.math.abs

/**
 * Serviço que exibe e gerencia a bolha flutuante sobre outros aplicativos.
 *
 * Dois elementos distintos compõem o overlay:
 *  - O **botão flutuante** (trigger circular persistente) cuja opacidade é configurável
 *    e que abre/fecha o card de resultado.
 *  - O **card de resultado** (balão com o veredito da corrida), sempre 100% opaco,
 *    com todas as dimensões (borda, corner radius, padding, tipografia) escaladas
 *    pelo multiplicador do [BubbleSize] escolhido.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var cardView: View? = null
    private var triggerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_HIDE) {
            removeEverything()
            stopSelf()
            return START_NOT_STICKY
        }

        currentResult?.let { res ->
            showTrigger()
            showCard(res)
        }

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
            // Mantém a opacidade atualizada a cada nova corrida.
            triggerView?.alpha = PreferencesRepository(this).getFloatingButtonOpacity() / 100f
            return
        }

        val opacity = PreferencesRepository(this).getFloatingButtonOpacity()
        val size = dp(60)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(120)
        }

        val trigger = TextView(this).apply {
            text = "🚗" // 🚗
            textSize = 26f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
                setStroke(dp(2), Color.parseColor("#B0FFFFFF"))
            }
            alpha = opacity / 100f
        }

        attachTriggerGestures(trigger, params, wm)
        triggerView = trigger

        try {
            wm.addView(trigger, params)
        } catch (_: Exception) {
            triggerView = null
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
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    params.x = initialX + dx.toInt()
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
                            toggleCard()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCard() {
        if (cardView != null) {
            removeCard()
        } else {
            currentResult?.let { showCard(it) }
        }
    }

    // ── Card de resultado ────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun showCard(res: ViabilityResult) {
        val wm = windowManager ?: return
        removeCard()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        val bubbleSize = PreferencesRepository(this).getBubbleSize()
        val view = createCardView(res, params, wm, bubbleSize)

        // Escala usada apenas para derivar dimensões em px; NÃO aplicamos
        // view.scaleX/scaleY — assim a borda, o corner radius e o padding
        // acompanham exatamente o mesmo multiplicador do conteúdo.
        cardView = view

        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            cardView = null
            return
        }

        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = Runnable { removeCard() }
        handler.postDelayed(autoDismissRunnable!!, 25000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        res: ViabilityResult,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        bubbleSize: BubbleSize
    ): View {
        // Multiplicador único: todas as dimensões derivam dele, garantindo
        // que borda, corner radius, padding e tipografia escalem juntos.
        val s = bubbleSize.scale

        val padH = (32 * s).toInt()
        val padV = (24 * s).toInt()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, padV)
        }

        val (bgColor, statusText) = when (res.status) {
            ViabilityStatus.EXCELLENT -> Pair(Color.parseColor("#1B5E20"), "🟢 EXCELENTE")
            ViabilityStatus.GOOD -> Pair(Color.parseColor("#2E7D32"), "🟢 BOA")
            ViabilityStatus.REGULAR -> Pair(Color.parseColor("#F57F17"), "🟡 REGULAR")
            ViabilityStatus.UNPROFITABLE -> Pair(Color.parseColor("#C62828"), "🔴 RECUSAR / PREJUÍZO")
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f * s
            setColor(bgColor)
            setStroke((2 * s).toInt().coerceAtLeast(1), Color.parseColor("#80FFFFFF"))
        }
        layout.background = bgDrawable

        // Cabeçalho (Status + Botão fechar)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "${res.offer.platform.displayName} • $statusText"
            setTextColor(Color.WHITE)
            textSize = 13f * s
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeSize = (60 * s).toInt()
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f * s
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(closeSize, closeSize)
            setOnClickListener { removeCard() }
        }

        header.addView(titleView)
        header.addView(closeBtn)
        layout.addView(header)

        val profitView = TextView(this).apply {
            text = "Lucro Líquido: R$ %.2f".format(Locale.US, res.netProfit)
            setTextColor(Color.WHITE)
            textSize = 17f * s
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(profitView)

        val ratesView = TextView(this).apply {
            text = "R$ %.2f/km  •  R$ %.1f/h".format(Locale.US, res.netPerKm, res.netHourlyRate)
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 13f * s
        }
        layout.addView(ratesView)

        val detailsView = TextView(this).apply {
            text = "Total: %.1f km (Busca: %.1f km) • %.0f min".format(
                Locale.US,
                res.offer.totalDistanceKm,
                res.offer.pickupDistanceKm,
                res.offer.totalDurationMinutes
            )
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 11f * s
        }
        layout.addView(detailsView)

        if (res.alerts.isNotEmpty()) {
            val alertView = TextView(this).apply {
                text = "⚠️ " + res.alerts.first()
                setTextColor(Color.parseColor("#FFF59D"))
                textSize = 11f * s
                typeface = Typeface.DEFAULT_BOLD
            }
            layout.addView(alertView)
        }

        // Arrastar o card
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        layout.setOnTouchListener { _, event ->
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
                        wm.updateViewLayout(layout, params)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        return layout
    }

    // ── Remoção / ciclo de vida ──────────────────────────────────────────────

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
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
