package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.RideOffer
import org.opendriver.data.PreferencesRepository
import org.opendriver.data.local.RideEntity
import org.opendriver.parser.LineReconstructor
import org.opendriver.parser.RideTextParser
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serviço de acessibilidade que captura ofertas de corrida da Uber/99.
 *
 * A árvore de acessibilidade dos apps de corrida NÃO expõe o texto da oferta
 * (só o chrome do sistema). Por isso, quando a leitura por nós não conclui nada,
 * tiramos um screenshot via [AccessibilityService.takeScreenshot] e rodamos OCR
 * (ML Kit) para extrair valor/distância/tempo — mesma técnica usada por apps
 * concorrentes.
 *
 * IMPORTANTE: todo I/O de arquivo e o processamento de OCR rodam fora da main
 * thread — este serviço compartilha a thread principal com o overlay; bloquear
 * a UI fazia o botão flutuante travar ao arrastar e o card demorar a sumir.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository

    // Trabalho pesado (I/O de log e persistência no Room) fora da main thread.
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Executor dedicado em background para o loop de captura e processamento gráfico (como o GigU)
    private val captureExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Deduplicação perceptual de frames (SAD 64x64) antes do OCR
    private val frameDeduplicator = FrameDeduplicator()

    private val isCapturing = AtomicBoolean(false)
    private var lastCaptureTime: Long = 0
    private var trailingCaptureFuture: ScheduledFuture<*>? = null
    private var periodicCheckFuture: ScheduledFuture<*>? = null
    private var currentForegroundPackage: String? = null

    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0
    private var eventsSeen: Int = 0
    private var lastCounterPersist: Long = 0
    private var lastLogAt: Long = 0

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = PreferencesRepository(this)
        eventsSeen = preferencesRepo.getReaderEventCount()
        warmUpOcr()
    }

    /** Carrega o modelo de OCR com bitmap e texto real já no start, eliminando o cold start. */
    private fun warmUpOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        captureExecutor.execute {
            try {
                val bmp = Bitmap.createBitmap(240, 80, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 18f
                    isAntiAlias = true
                }
                canvas.drawText("R$ 25,90", 10f, 30f, paint)
                canvas.drawText("12 min • 5.4 km Aceitar", 10f, 60f, paint)

                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnCompleteListener(captureExecutor) {
                        bmp.recycle()
                        Log.i(TAG, "ML Kit OCR pré-aquecido com sucesso!")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Falha no warmUpOcr: ${e.message}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (!isTargetApp(packageName)) {
            stopPeriodicPolling()
            currentForegroundPackage = null
            return
        }

        currentForegroundPackage = packageName
        countEvent()

        if (!preferencesRepo.getCopilotEnabled()) return

        startPeriodicPollingIfNeeded()

        // 1) Tentativa barata: ler o texto da árvore de acessibilidade.
        val texts = LinkedHashSet<String>()
        for (i in event.text.indices) {
            event.text[i]?.toString()?.let { normalize(it) }?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        }
        event.contentDescription?.toString()?.let { normalize(it) }?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }

        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() != this.packageName) {
            extractTextNodes(activeRoot, texts)
        }
        try {
            windows?.forEach { window ->
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() == this.packageName) return@forEach
                extractTextNodes(root, texts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enumerar janelas: ${e.message}")
        }

        if (texts.isNotEmpty()) {
            appendCaptureLog("tree", packageName, event.eventType, texts)
            if (looksLikeOffer(texts) && hasAcceptSignal(texts)) {
                RideTextParser.parse(packageName, texts.toList())?.let { offer ->
                    if (publishOffer(offer, packageName)) return
                }
            }
        }

        // 2) A oferta da Uber não aparece na árvore: captura reativa via screenshot + OCR.
        requestScreenshot(packageName)
    }

    // ── Loop de Captura Reativo e Periódico (estilo GigU) ──────────────────────

    private fun startPeriodicPollingIfNeeded() {
        if (periodicCheckFuture == null || periodicCheckFuture?.isDone == true) {
            periodicCheckFuture = captureExecutor.scheduleWithFixedDelay({
                val pkg = currentForegroundPackage
                if (pkg != null && isTargetApp(pkg) && preferencesRepo.getCopilotEnabled()) {
                    requestScreenshot(pkg)
                }
            }, 1000L, 1000L, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopPeriodicPolling() {
        periodicCheckFuture?.cancel(false)
        periodicCheckFuture = null
    }

    private fun scheduleTrailingCapture(packageName: String, delayMs: Long) {
        val safeDelay = delayMs.coerceAtLeast(50L)
        trailingCaptureFuture?.cancel(false)
        trailingCaptureFuture = captureExecutor.schedule({
            requestScreenshot(packageName)
        }, safeDelay, TimeUnit.MILLISECONDS)
    }

    private fun requestScreenshot(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastCaptureTime

        // Se uma captura já está em andamento, agenda trailing capture para logo após
        if (isCapturing.get()) {
            scheduleTrailingCapture(packageName, MIN_CAPTURE_INTERVAL_MS)
            return
        }

        // Se ainda não passou o intervalo mínimo entre capturas, agenda para quando liberar
        if (elapsed < MIN_CAPTURE_INTERVAL_MS) {
            scheduleTrailingCapture(packageName, MIN_CAPTURE_INTERVAL_MS - elapsed)
            return
        }

        trailingCaptureFuture?.cancel(false)
        trailingCaptureFuture = null
        isCapturing.set(true)
        lastCaptureTime = now

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                captureExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        try {
                            val buffer = result.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            buffer.close()
                            if (bitmap == null) {
                                Log.w(TAG, "OCR: bitmap nulo")
                                isCapturing.set(false)
                                return
                            }
                            runOcr(packageName, bitmap)
                        } catch (e: Exception) {
                            Log.w(TAG, "Erro processando screenshot: ${e.message}")
                            isCapturing.set(false)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        isCapturing.set(false)
                        Log.w(TAG, "takeScreenshot falhou: $errorCode")
                        appendLogLine("screenshot FAIL code=$errorCode pkg=$packageName")
                        if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                            scheduleTrailingCapture(packageName, 500L)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            isCapturing.set(false)
            Log.w(TAG, "takeScreenshot exceção: ${e.message}")
        }
    }

    private fun runOcr(packageName: String, raw: Bitmap) {
        try {
            // O screenshot vem como hardware bitmap: converte para software fora da main thread
            val software = if (raw.config == Bitmap.Config.HARDWARE) {
                val copy = raw.copy(Bitmap.Config.ARGB_8888, false)
                raw.recycle()
                copy
            } else raw
            if (software == null) {
                isCapturing.set(false)
                return
            }

            val cropped = cropOfferRegion(software)
            if (cropped !== software) software.recycle()

            // Deduplicação perceptual (SAD 64x64): descarta se a tela for quase idêntica ao frame anterior
            if (frameDeduplicator.shouldSkip(cropped, packageName)) {
                cropped.recycle()
                isCapturing.set(false)
                return
            }

            val image = InputImage.fromBitmap(cropped, 0)
            recognizer.process(image)
                .addOnSuccessListener(captureExecutor) { vision ->
                    isCapturing.set(false)
                    val lines = LineReconstructor.reconstruct(vision)
                    cropped.recycle()

                    appendCaptureLog("ocr", packageName, -1, lines)

                    if (hasAcceptSignal(lines)) {
                        val offer = RideTextParser.parse(packageName, lines)
                        if (offer != null) publishOffer(offer, packageName)
                    } else if (currentForegroundPackage == packageName) {
                        // Card ainda em animação de subida: agenda nova leitura imediata pós-animação
                        scheduleTrailingCapture(packageName, 500L)
                    }
                }
                .addOnFailureListener(captureExecutor) { e ->
                    isCapturing.set(false)
                    cropped.recycle()
                    Log.w(TAG, "OCR falhou: ${e.message}")
                }
        } catch (e: Exception) {
            isCapturing.set(false)
            Log.w(TAG, "Exceção no runOcr: ${e.message}")
        }
    }

    /**
     * Recorta a faixa da oferta (descartando topo e barra de navegação inferior) e reduz para 720px,
     * acelerando a inferência do ML Kit de ~850ms para ~300ms.
     */
    private fun cropOfferRegion(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src

        val top = (h * OFFER_TOP_FRACTION).toInt().coerceIn(0, h - 1)
        val bottom = (h * OFFER_BOTTOM_FRACTION).toInt().coerceIn(top + 1, h)
        val cropH = bottom - top

        val region = try {
            Bitmap.createBitmap(src, 0, top, w, cropH)
        } catch (_: Exception) {
            src
        }

        if (region.width <= OCR_MAX_WIDTH) return region
        val newH = (region.height * (OCR_MAX_WIDTH.toFloat() / region.width)).toInt().coerceAtLeast(1)
        val scaled = try {
            Bitmap.createScaledBitmap(region, OCR_MAX_WIDTH, newH, true)
        } catch (_: Exception) {
            region
        }
        if (scaled !== region) region.recycle()
        return scaled
    }

    // ── Publicação do resultado ──────────────────────────────────────────────

    private fun publishOffer(offer: RideOffer, packageName: String): Boolean {
        val key = "${offer.platform}_${offer.grossFare}_${offer.totalDistanceKm}"
        val now = System.currentTimeMillis()
        if (key == lastOfferKey && now - lastOfferTimestamp < 8000) return false

        lastOfferKey = key
        lastOfferTimestamp = now
        Log.i(TAG, "Oferta reconhecida: $offer")

        val profile = preferencesRepo.getVehicleProfile()
        val prefs = preferencesRepo.getDriverPreferences()
        val result = ViabilityEngine.evaluate(offer, profile, prefs)

        persistRelevantCapture(
            "pkg=$packageName • OK\n" +
                "R$ ${"%.2f".format(result.offer.grossFare)} • " +
                "${"%.1f".format(result.offer.totalDistanceKm)} km • " +
                "lucro R$ ${"%.2f".format(result.netProfit)} • ${result.status.label}"
        )

        val entity = RideEntity(
            timestamp = System.currentTimeMillis(),
            platform = result.offer.platform.displayName,
            grossFare = result.offer.grossFare,
            totalDistanceKm = result.offer.totalDistanceKm,
            totalDurationMinutes = result.offer.totalDurationMinutes,
            netProfit = result.netProfit,
            grossPerKm = result.grossPerKm,
            netPerKm = result.netPerKm,
            grossHourlyRate = result.grossHourlyRate,
            netHourlyRate = result.netHourlyRate,
            status = result.status.label,
            pickupAddress = result.offer.pickupAddress,
            destinationAddress = result.offer.destinationAddress,
            passengerRating = result.offer.passengerRating
        )
        ioExecutor.execute { preferencesRepo.saveRide(entity) }

        FloatingOverlayService.showResult(this, result)
        return true
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private fun countEvent() {
        eventsSeen++
        val now = System.currentTimeMillis()
        if (now - lastCounterPersist >= 1000) {
            lastCounterPersist = now
            preferencesRepo.setReaderEventCount(eventsSeen)
        }
    }

    /** Só processa apps que o usuário deixou selecionados (Uber, 99, InDrive). */
    private fun isTargetApp(packageName: String): Boolean {
        val selected = preferencesRepo.getSelectedApps()
        return when {
            packageName.contains("uber", ignoreCase = true) -> "Uber" in selected
            packageName.contains("app99", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) -> "99" in selected
            packageName.contains("indrive", ignoreCase = true) -> "InDrive" in selected
            else -> false
        }
    }

    private fun hasAcceptSignal(texts: Collection<String>): Boolean =
        texts.any { ACCEPT_REGEX.containsMatchIn(it) }

    private fun looksLikeOffer(texts: Collection<String>): Boolean {
        var maxFare = 0.0
        var hasDistance = false
        var hasDuration = false
        for (t in texts) {
            FARE_REGEX.find(t)?.let { m ->
                val v = m.groupValues[1].replace(",", ".").toDoubleOrNull()
                if (v != null && v > maxFare) maxFare = v
            }
            if (DISTANCE_REGEX.containsMatchIn(t)) hasDistance = true
            if (DURATION_REGEX.containsMatchIn(t)) hasDuration = true
        }
        return maxFare > 1.0 && (hasDistance || hasDuration)
    }

    private fun normalize(raw: String): String =
        raw.replace(' ', ' ').replace(' ', ' ').trim()

    private fun extractTextNodes(node: AccessibilityNodeInfo?, output: MutableCollection<String>) {
        if (node == null) return
        node.text?.toString()?.let { normalize(it) }?.let { if (it.isNotEmpty()) output.add(it) }
        node.contentDescription?.toString()?.let { normalize(it) }?.let { if (it.isNotEmpty()) output.add(it) }
        for (i in 0 until node.childCount) {
            extractTextNodes(node.getChild(i), output)
        }
    }

    private fun mediaDir(): File? = getExternalMediaDirs()?.firstOrNull() ?: getExternalFilesDir(null)

    private fun appendCaptureLog(source: String, packageName: String, eventType: Int, texts: Collection<String>) {
        val now = System.currentTimeMillis()
        if (now - lastLogAt < 500) return
        lastLogAt = now
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
        appendLogLine(
            "---- $stamp • $source • pkg=$packageName • ev=$eventType • n=${texts.size}\n" +
                texts.joinToString(" | ").take(1500)
        )
    }

    /** Append em background (nunca na main thread) com poda do arquivo. */
    private fun appendLogLine(content: String) {
        ioExecutor.execute {
            try {
                val dir = mediaDir() ?: return@execute
                val file = File(dir, "capture_log.txt")
                if (file.exists() && file.length() > 200_000) {
                    file.writeText(file.readText().takeLast(100_000))
                }
                FileOutputStream(file, true).use { it.write((content + "\n").toByteArray()) }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gravar log: ${e.message}")
            }
        }
    }

    private fun persistRelevantCapture(info: String) {
        preferencesRepo.setLastCaptureInfo(info)
        ioExecutor.execute {
            try {
                mediaDir()?.let { File(it, "last_capture.txt").writeText(info) }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gravar captura: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        stopPeriodicPolling()
        trailingCaptureFuture?.cancel(false)
        frameDeduplicator.reset()
        captureExecutor.shutdown()
        ioExecutor.shutdown()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "OpenDriver"
        // OCR ultra-rápido: recorta a região da oferta (28% a 95% da tela) e reduz largura para 720px
        private const val OFFER_TOP_FRACTION = 0.28f
        private const val OFFER_BOTTOM_FRACTION = 0.95f
        private const val OCR_MAX_WIDTH = 720
        private const val MIN_CAPTURE_INTERVAL_MS = 450L
        private const val SP = "[\\s\\u00A0\\u202F]"
        private val FARE_REGEX = Regex("R\\$" + SP + "*([0-9]+[.,][0-9]{2})", RegexOption.IGNORE_CASE)
        private val DISTANCE_REGEX = Regex("[0-9]+(?:[.,][0-9]+)?" + SP + "*km", RegexOption.IGNORE_CASE)
        private val DURATION_REGEX = Regex("[0-9]+" + SP + "*min", RegexOption.IGNORE_CASE)

        // Uber: "Aceitar" só em corrida exclusiva; no radar a maioria mostra "Selecionar".
        // 99/inDrive usam "Aceitar". Cobrimos todas as variantes do botão de aceite e cabeçalhos de oferta.
        private val ACCEPT_REGEX = Regex(
            "Aceitar|Recusar|Selecionar|Escolher|Nova viagem|Nova corrida|99Pop|99Comfort|99Moto|99Negocia|99Entrega|99Plus|99Taxi|99Táxi",
            RegexOption.IGNORE_CASE
        )
    }
}
