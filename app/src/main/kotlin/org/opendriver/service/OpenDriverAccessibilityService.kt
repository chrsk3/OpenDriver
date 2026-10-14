package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.core.model.RideOffer
import org.opendriver.data.PreferencesRepository
import org.opendriver.parser.RideTextParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço de acessibilidade que captura ofertas de corrida da Uber/99.
 *
 * A árvore de acessibilidade dos apps de corrida NÃO expõe o texto da oferta
 * (só o chrome do sistema). Por isso, quando a leitura por nós não conclui nada,
 * tiramos um screenshot via [AccessibilityService.takeScreenshot] e rodamos OCR
 * (ML Kit) para extrair valor/distância/tempo — mesma técnica usada por apps
 * concorrentes.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository
    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0
    private var eventsSeen: Int = 0
    private var lastCounterPersist: Long = 0
    private var lastLogAt: Long = 0
    private var lastOcrAt: Long = 0

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = PreferencesRepository(this)
        eventsSeen = preferencesRepo.getReaderEventCount()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (!isTargetApp(packageName)) return

        countEvent()

        if (!preferencesRepo.getCopilotEnabled()) return

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

        // 2) A oferta da Uber não aparece na árvore: tenta OCR da tela.
        maybeRunOcr(packageName)
    }

    // ── OCR (screenshot + ML Kit) ────────────────────────────────────────────

    private fun maybeRunOcr(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // takeScreenshot é API 30+

        val now = System.currentTimeMillis()
        if (now - lastOcrAt < 2500) return
        lastOcrAt = now

        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                ContextCompat.getMainExecutor(this),
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        buffer.close()
                        if (bitmap == null) {
                            Log.w(TAG, "OCR: bitmap nulo")
                            return
                        }
                        runOcr(packageName, bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot falhou: $errorCode")
                        appendLogLine("screenshot FAIL code=$errorCode pkg=$packageName")
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "takeScreenshot exceção: ${e.message}")
        }
    }

    private fun runOcr(packageName: String, bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { vision ->
                val lines = mutableListOf<String>()
                for (block in vision.textBlocks) {
                    for (line in block.lines) {
                        val t = normalize(line.text)
                        if (t.isNotEmpty()) lines.add(t)
                    }
                }
                bitmap.recycle()

                appendCaptureLog("ocr", packageName, -1, lines)

                // A tela de oferta real sempre tem botão de aceite; home/oportunidades não.
                // Exigir esse sinal elimina cards de corridas que não existiram.
                if (hasAcceptSignal(lines)) {
                    val offer = RideTextParser.parse(packageName, lines)
                    if (offer != null) publishOffer(offer, packageName)
                }
            }
            .addOnFailureListener { e ->
                bitmap.recycle()
                Log.w(TAG, "OCR falhou: ${e.message}")
            }
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

    private fun isTargetApp(packageName: String): Boolean =
        packageName.contains("uber", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) ||
                packageName.contains("indrive", ignoreCase = true)

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

    private fun appendLogLine(content: String) {
        try {
            val dir = mediaDir() ?: return
            val file = File(dir, "capture_log.txt")
            val merged = (if (file.exists()) file.readText() else "") + content + "\n"
            file.writeText(if (merged.length > 160000) merged.takeLast(160000) else merged)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar log: ${e.message}")
        }
    }

    private fun appendCaptureLog(source: String, packageName: String, eventType: Int, texts: Collection<String>) {
        val now = System.currentTimeMillis()
        if (now - lastLogAt < 500) return
        lastLogAt = now
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
        val entry = "---- $stamp • $source • pkg=$packageName • ev=$eventType • n=${texts.size}\n" +
            texts.joinToString(" | ").take(1500)
        appendLogLine(entry)
    }

    private fun persistRelevantCapture(info: String) {
        preferencesRepo.setLastCaptureInfo(info)
        try {
            mediaDir()?.let { File(it, "last_capture.txt").writeText(info) }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar captura: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "OpenDriver"
        private const val SP = "[\\s\\u00A0\\u202F]"
        private val FARE_REGEX = Regex("R\\$" + SP + "*([0-9]+[.,][0-9]{2})", RegexOption.IGNORE_CASE)
        private val DISTANCE_REGEX = Regex("[0-9]+(?:[.,][0-9]+)?" + SP + "*km", RegexOption.IGNORE_CASE)
        private val DURATION_REGEX = Regex("[0-9]+" + SP + "*min", RegexOption.IGNORE_CASE)
        private val ACCEPT_REGEX = Regex("Aceitar|Recusar|Nova viagem|Nova corrida", RegexOption.IGNORE_CASE)
    }
}
