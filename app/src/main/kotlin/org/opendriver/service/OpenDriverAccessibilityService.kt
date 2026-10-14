package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.data.PreferencesRepository
import org.opendriver.parser.RideTextParser

/**
 * Serviço de acessibilidade que monitora a tela para capturar dados
 * de corridas da Uber e 99 no instante em que a chamada toca.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository
    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0
    private var lastDebugAt: Long = 0

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = PreferencesRepository(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Só processa eventos de apps de corrida monitorados
        if (!isTargetApp(packageName)) return

        // Copiloto e Cálculo de Ganhos precisam estar ligados
        if (!preferencesRepo.getCopilotEnabled() || !preferencesRepo.getEarningsCalcEnabled()) {
            persistDebug("pkg=$packageName • leitura pausada (Copiloto/Cálculo desligado)")
            return
        }

        // Extrai o conteúdo visível de TODAS as janelas: a oferta da Uber/99 costuma
        // aparecer num popup (janela secundária) e não na janela ativa do app base.
        // Ignora a própria janela do OpenDriver para não capturar o texto do overlay.
        val ownPackage = this.packageName
        val texts = LinkedHashSet<String>()
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

        if (texts.isEmpty()) {
            persistDebug("pkg=$packageName • evento=${event.eventType} • nenhum texto capturado")
            return
        }

        // Analisa o texto capturado procurando uma oferta válida
        val offer = RideTextParser.parse(packageName, texts.toList())
        if (offer == null) {
            val preview = texts.take(12).joinToString(" | ").take(400)
            persistDebug("pkg=$packageName • ${texts.size} textos, oferta NÃO reconhecida\n[$preview]")
            return
        }

        // Evita reprocessar a mesma oferta repetidamente em intervalos menores que 6 segundos
        val offerKey = "${offer.platform}_${offer.grossFare}_${offer.totalDistanceKm}"
        val now = System.currentTimeMillis()
        if (offerKey == lastOfferKey && (now - lastOfferTimestamp) < 6000) {
            return
        }

        lastOfferKey = offerKey
        lastOfferTimestamp = now

        Log.i(TAG, "Oferta reconhecida: $offer")

        // Carrega as configurações do motorista e calcula a viabilidade
        val profile = preferencesRepo.getVehicleProfile()
        val preferences = preferencesRepo.getDriverPreferences()
        val result = ViabilityEngine.evaluate(offer, profile, preferences)

        persistDebug(
            "pkg=$packageName • OK\n" +
                "R$ ${"%.2f".format(result.offer.grossFare)} • " +
                "${"%.1f".format(result.offer.totalDistanceKm)} km • " +
                "lucro R$ ${"%.2f".format(result.netProfit)} • ${result.status.label}"
        )

        // Envia o resultado para a bolha flutuante
        FloatingOverlayService.showResult(this, result)
    }

    private fun isTargetApp(packageName: String): Boolean =
        packageName.contains("ubercab", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) ||
                packageName.contains("indrive", ignoreCase = true)

    private fun persistDebug(info: String) {
        val now = System.currentTimeMillis()
        if (now - lastDebugAt < 1000) return
        lastDebugAt = now
        preferencesRepo.setLastCaptureInfo(info)
    }

    private fun extractTextNodes(node: AccessibilityNodeInfo?, output: MutableCollection<String>) {
        if (node == null) return

        node.text?.toString()?.trim()?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        node.contentDescription?.toString()?.trim()?.let {
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
    }
}
