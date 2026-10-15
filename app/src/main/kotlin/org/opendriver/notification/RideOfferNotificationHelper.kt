package org.opendriver.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import org.opendriver.R
import org.opendriver.core.model.ViabilityResult
import org.opendriver.core.model.ViabilityStatus
import org.opendriver.ui.MainActivity
import java.net.URLEncoder
import java.util.Locale

/**
 * Helper responsável por construir e exibir a notificação rica de ofertas de corrida.
 *
 * Implementa paridade com o GigU:
 * 1. Layout visual personalizado via RemoteViews (valor, km, tempo, semáforo e lucro).
 * 2. Botões de ação para abrir o Google Maps nos endereços de Embarque e Destino.
 * 3. Comportamento anti-dismiss: a notificação NÃO se auto-cancela ao ser tocada (setAutoCancel(false)).
 * 4. URLs de Maps no formato: https://www.google.com/maps/search/?api=1&query=ENDEREÇO_ENCODED.
 * 5. Canal de alta prioridade (IMPORTANCE_HIGH) para exibição heads-up.
 */
object RideOfferNotificationHelper {

    const val CHANNEL_OFFERS_ID = "opendriver_offers_channel"
    const val NOTIFICATION_OFFER_ID = 2002

    private const val REQUEST_CODE_CONTENT = 2000
    private const val REQUEST_CODE_PICKUP = 2001
    private const val REQUEST_CODE_DEST = 2002

    /**
     * Cria e registra o canal de notificação de alta prioridade para ofertas.
     */
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OFFERS_ID,
                "Ofertas de Corridas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações visuais e alertas de viabilidade de novas ofertas"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Monta o PendingIntent para abrir o Google Maps via URL padrão:
     * https://www.google.com/maps/search/?api=1&query=ENDEREÇO_ENCODED
     */
    fun createMapsPendingIntent(
        context: Context,
        rawAddress: String?,
        fallbackQuery: String,
        requestCode: Int
    ): PendingIntent {
        val query = rawAddress?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackQuery
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query
        }
        val url = "https://www.google.com/maps/search/?api=1&query=$encodedQuery"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * PendingIntent disparado quando o motorista toca no corpo da notificação.
     * Abre a tela principal do OpenDriver sem descartar a notificação.
     */
    fun createContentPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_CODE_CONTENT, intent, flags)
    }

    /**
     * Constrói e envia a notificação rica com RemoteViews, botões de ação e canal de alta prioridade.
     */
    fun postRideOfferNotification(context: Context, res: ViabilityResult) {
        ensureNotificationChannel(context)

        val offer = res.offer
        val remoteViews = RemoteViews(context.packageName, R.layout.notification_ride_offer)

        // 1. Tag de Plataforma (UBER, 99, etc.)
        remoteViews.setTextViewText(
            R.id.notif_platform,
            offer.platform.displayName.uppercase(Locale.ROOT)
        )

        // 2. Semáforo / Badge de Viabilidade com cor indicativa
        val statusColor = when (res.status) {
            ViabilityStatus.EXCELLENT -> Color.parseColor("#00C853") // Verde
            ViabilityStatus.GOOD -> Color.parseColor("#64DD17")      // Verde claro
            ViabilityStatus.REGULAR -> Color.parseColor("#FFD600")   // Amarelo
            ViabilityStatus.UNPROFITABLE -> Color.parseColor("#D50000") // Vermelho
        }
        remoteViews.setTextViewText(
            R.id.notif_status_badge,
            res.status.label.uppercase(Locale.ROOT)
        )
        remoteViews.setInt(R.id.notif_status_badge, "setBackgroundColor", statusColor)

        // Nota do passageiro (se disponível)
        val rating = offer.passengerRating
        if (rating != null && rating > 0.0) {
            remoteViews.setTextViewText(
                R.id.notif_rating,
                "★ %.2f".format(Locale.US, rating)
            )
            remoteViews.setViewVisibility(R.id.notif_rating, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.notif_rating, View.GONE)
        }

        // 3. Métricas principais: Valor (R$), Distância (km) e Tempo (min)
        val fareText = "R$ %.2f".format(Locale.US, offer.grossFare)
        val distText = "%.1f km".format(Locale.US, offer.totalDistanceKm)
        val durationMinutes = offer.totalDurationMinutes.toInt().coerceAtLeast(1)
        val durationText = "$durationMinutes min"

        remoteViews.setTextViewText(R.id.notif_fare, fareText)
        remoteViews.setTextViewText(R.id.notif_distance, distText)
        remoteViews.setTextViewText(R.id.notif_duration, durationText)

        // 4. Métricas econômicas: Lucro Líquido, R$/km e R$/h
        val profitText = "Lucro R$ %.2f".format(Locale.US, res.netProfit)
        val ratesText = "R$ %.2f/km · R$ %.1f/h".format(Locale.US, res.netPerKm, res.netHourlyRate)
        remoteViews.setTextViewText(R.id.notif_profit, profitText)
        remoteViews.setTextViewText(R.id.notif_rates, ratesText)

        // 5. Endereços (se extraídos ou fallback)
        val pickupAddress = offer.pickupAddress?.trim().orEmpty()
        val destAddress = offer.destinationAddress?.trim().orEmpty()

        if (pickupAddress.isNotEmpty()) {
            remoteViews.setTextViewText(R.id.notif_pickup_address, pickupAddress)
            remoteViews.setViewVisibility(R.id.notif_pickup_container, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.notif_pickup_container, View.GONE)
        }

        if (destAddress.isNotEmpty()) {
            remoteViews.setTextViewText(R.id.notif_dest_address, destAddress)
            remoteViews.setViewVisibility(R.id.notif_dest_container, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.notif_dest_container, View.GONE)
        }

        // 6. Intents para Google Maps (Embarque e Destino)
        val pickupPendingIntent = createMapsPendingIntent(
            context,
            offer.pickupAddress,
            fallbackQuery = "Embarque",
            requestCode = REQUEST_CODE_PICKUP
        )
        val destPendingIntent = createMapsPendingIntent(
            context,
            offer.destinationAddress,
            fallbackQuery = "Destino",
            requestCode = REQUEST_CODE_DEST
        )

        // Vincula cliques aos botões da RemoteViews
        remoteViews.setOnClickPendingIntent(R.id.notif_btn_pickup, pickupPendingIntent)
        remoteViews.setOnClickPendingIntent(R.id.notif_btn_dest, destPendingIntent)

        val contentPendingIntent = createContentPendingIntent(context)

        val summaryText = "$fareText • $distText • $durationText • ${res.status.label}"

        val builder = NotificationCompat.Builder(context, CHANNEL_OFFERS_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setCustomHeadsUpContentView(remoteViews)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // ANTI-DISMISS: a notificação não é descartada ao tocar nela ou nos botões
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .setContentTitle("OpenDriver • ${res.status.label}")
            .setContentText(summaryText)
            .addAction(android.R.drawable.ic_dialog_map, "Embarque", pickupPendingIntent)
            .addAction(android.R.drawable.ic_dialog_map, "Destino", destPendingIntent)

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_OFFER_ID, builder.build())
    }
}
