package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.data.PreferencesRepository
import org.opendriver.parser.RideTextParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço de acessibilidade que monitora a tela para capturar dados
 * de corridas da Uber e 99 no instante em que a chamada toca.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository
    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0
    private var eventsSeen: Int = 0
    private var lastCounterPersist: Long = 0
    private var lastLogAt: Long = 0

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

        if (!preferencesRepo.getCopilotEnabled() || !preferencesRepo.getEarningsCalcEnabled()) {
            return
        }

        val ownPackage = this.packageName
        val texts = LinkedHashSet<String>()

        // 1) Texto carregado no próprio evento — captura direta do que mudou na tela.
        for (i in event.text.indices) {
            event.text[i]?.toString()?.let { normalize(it) }?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        }
        event.contentDescription?.toString()?.let { normalize(it) }?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }

        // 2) Varredura das janelas (oferta costuma vir em popup/janela secundária).
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() != ownPackage) {
            extractTextNodes(activeRoot, texts)
        }
        try {
            windows?.forEach { window ->
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() == ownPackage) return@forEach
                extractTextNodes(root, texts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enumerar janelas: ${e.message}")
        }

        if (texts.isEmpty()) return

        appendCaptureLog(packageName, event.eventType, texts)

        // Ignora ruído (home offline "R$ 0,00", Recentes, barra de notificações):
        // só registra/analisa capturas que tenham valor real (> R$1) E distância/tempo.
        if (!looksLikeOffer(texts)) return

        val offer = RideTextParser.parse(packageName, texts.toList())
        if (offer == null) {
            persistRelevantCapture(
                "pkg=$packageName • ${texts.size} textos, oferta NÃO reconhecida\n" +
                    "[" + texts.joinToString(" | ").take(1600) + "]"
            )
            return
        }

        val offerKey = "${offer.platform}_${offer.grossFare}_${offer.totalDistanceKm}"
        val now = System.currentTimeMillis()
        if (offerKey == lastOfferKey && (now - lastOfferTimestamp) < 6000) return

        lastOfferKey = offerKey
        lastOfferTimestamp = now
        Log.i(TAG, "Oferta reconhecida: $offer")

        val profile = preferencesRepo.getVehicleProfile()
        val preferences = preferencesRepo.getDriverPreferences()
        val result = ViabilityEngine.evaluate(offer, profile, preferences)

        persistRelevantCapture(
            "pkg=$packageName • OK\n" +
                "R$ ${"%.2f".format(result.offer.grossFare)} • " +
                "${"%.1f".format(result.offer.totalDistanceKm)} km • " +
                "lucro R$ ${"%.2f".format(result.netProfit)} • ${result.status.label}"
        )

        FloatingOverlayService.showResult(this, result)
    }

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

    /**
     * Heurística para não confundir a home (que tem "R$ 0,00") com uma oferta:
     * exige um valor monetário real (> R$1) E sinal de distância (km) ou tempo (min).
     */
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

    private fun persistRelevantCapture(info: String) {
        preferencesRepo.setLastCaptureInfo(info)
        writeCaptureFile("last_capture.txt", info)
    }

    /** Log rolante (append, com teto) de tudo que for lido — permite achar o momento da oferta. */
    private fun appendCaptureLog(packageName: String, eventType: Int, texts: Collection<String>) {
        val now = System.currentTimeMillis()
        if (now - lastLogAt < 500) return
        lastLogAt = now
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
        val entry = "---- $stamp • pkg=$packageName • ev=$eventType • n=${texts.size}\n" +
            texts.joinToString(" | ").take(1200) + "\n"
        try {
            val dir = getExternalMediaDirs()?.firstOrNull() ?: getExternalFilesDir(null) ?: return
            val file = File(dir, "capture_log.txt")
            val merged = (if (file.exists()) file.readText() else "") + entry
            file.writeText(if (merged.length > 120000) merged.takeLast(120000) else merged)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar capture_log.txt: ${e.message}")
        }
    }

    private fun writeCaptureFile(name: String, content: String) {
        // Android/media/<pkg> é acessível pelo gerenciador de arquivos e legível de fora do app.
        try {
            val dir = getExternalMediaDirs()?.firstOrNull() ?: getExternalFilesDir(null)
            dir?.let { File(it, name).writeText(content) }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar $name: ${e.message}")
        }
    }

    /** Normaliza espaços unicode (NBSP) que quebram os regex de valor/distância. */
    private fun normalize(raw: String): String =
        raw.replace(' ', ' ').replace(' ', ' ').trim()

    private fun extractTextNodes(node: AccessibilityNodeInfo?, output: MutableCollection<String>) {
        if (node == null) return

        node.text?.toString()?.let { normalize(it) }?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        node.contentDescription?.toString()?.let { normalize(it) }?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        for (i in 0 until node.childCount) {
            extractTextNodes(node.getChild(i), output)
        }
    }

    override fun onInterrupt() {
        // Chamado quando o sistema interrompe o serviço de acessibilidade
    }

    companion object {
        private const val TAG = "OpenDriver"

        private const val SP = "[\\s\\u00A0\\u202F]"
        private val FARE_REGEX = Regex("R\\$" + SP + "*([0-9]+[.,][0-9]{2})", RegexOption.IGNORE_CASE)
        private val DISTANCE_REGEX = Regex("[0-9]+(?:[.,][0-9]+)?" + SP + "*km", RegexOption.IGNORE_CASE)
        private val DURATION_REGEX = Regex("[0-9]+" + SP + "*min", RegexOption.IGNORE_CASE)
    }
}
