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

/**
 * Serviço que exibe e gerencia a bolha flutuante sobre outros aplicativos.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_HIDE) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        currentResult?.let { res ->
            showOverlay(res)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay(res: ViabilityResult) {
        removeOverlay()

        val wm = windowManager ?: return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 150
        }

        val prefs = PreferencesRepository(this)
        val bubbleSize = prefs.getBubbleSize()
        val opacity = prefs.getBubbleOpacity()

        val view = createOverlayView(res, params, wm)

        // Aplica escala conforme tamanho escolhido
        view.scaleX = bubbleSize.scale
        view.scaleY = bubbleSize.scale

        // Aplica opacidade (10% a 100%)
        view.alpha = opacity / 100f

        overlayView = view

        try {
            wm.addView(view, params)
        } catch (_: Exception) {
            return
        }

        // Auto-dismiss após 25 segundos se o usuário não fechar
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = Runnable {
            removeOverlay()
        }
        handler.postDelayed(autoDismissRunnable!!, 25000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView(
        res: ViabilityResult,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        // Cor de fundo conforme viabilidade
        val (bgColor, statusText) = when (res.status) {
            ViabilityStatus.EXCELLENT -> Pair(Color.parseColor("#1B5E20"), "🟢 EXCELENTE")
            ViabilityStatus.GOOD -> Pair(Color.parseColor("#2E7D32"), "🟢 BOA")
            ViabilityStatus.REGULAR -> Pair(Color.parseColor("#F57F17"), "🟡 REGULAR")
            ViabilityStatus.UNPROFITABLE -> Pair(Color.parseColor("#C62828"), "🔴 RECUSAR / PREJUÍZO")
        }

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(bgColor)
            setStroke(2, Color.parseColor("#80FFFFFF"))
        }
        layout.background = bgDrawable

        // Linha do cabeçalho (Status + Botão fechar)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            text = "${res.offer.platform.displayName} • $statusText"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 12f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(60, 60)
            setOnClickListener {
                removeOverlay()
            }
        }

        header.addView(titleView)
        header.addView(closeBtn)
        layout.addView(header)

        // Lucro Líquido
        val profitView = TextView(this).apply {
            text = "Lucro Líquido: R$ %.2f".format(Locale.US, res.netProfit)
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(profitView)

        // Métricas unitárias
        val ratesView = TextView(this).apply {
            text = "R$ %.2f/km  •  R$ %.1f/h".format(Locale.US, res.netPerKm, res.netHourlyRate)
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 13f
        }
        layout.addView(ratesView)

        // Detalhes da distância e tempo
        val detailsView = TextView(this).apply {
            text = "Total: %.1f km (Busca: %.1f km) • %.0f min".format(
                Locale.US,
                res.offer.totalDistanceKm,
                res.offer.pickupDistanceKm,
                res.offer.totalDurationMinutes
            )
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 11f
        }
        layout.addView(detailsView)

        // Alertas rápidos (se houver)
        if (res.alerts.isNotEmpty()) {
            val alertView = TextView(this).apply {
                text = "⚠️ " + res.alerts.first()
                setTextColor(Color.parseColor("#FFF59D"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
            }
            layout.addView(alertView)
        }

        // Lógica de arrastar a bolha na tela
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

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
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
