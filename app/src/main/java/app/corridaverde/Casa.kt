package app.corridaverde

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Ponto(val lat: Double, val lon: Double)

/** Modo "Indo pra casa": quanto falta do destino da oferta até a casa do motorista. */
object Casa {
    /** Distância em linha reta (fórmula de haversine). */
    fun distanciaKm(a: Ponto, b: Ponto): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun perto(km: Double, raioKm: Double) = km <= raioKm

    /** A casinha só aparece quando é perto: de relance, casinha verde quer dizer "serve para voltar". */
    fun texto(km: Double, raioKm: Double): String {
        val d = if (km < 10) String.format(PT, "%.1f km", km) else "${Math.round(km)} km"
        return if (perto(km, raioKm)) "🏠 Perto de casa · $d" else "$d de casa"
    }

    /** O destino como a Uber escreve ("Ambience Vila Mariana, Vila Mariana, São Paulo"), pronto para o mapa. */
    fun consulta(destino: String): String {
        val t = destino.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim().trimEnd(',')
        return if (t.contains("Brasil", ignoreCase = true)) t else "$t, Brasil"
    }
}

/**
 * Transforma endereço em ponto no mapa pelo serviço do próprio Android (sem chave nem custo; usa a internet).
 * Destinos já consultados ficam guardados no celular e voltam na hora.
 * Chamar fora da linha da tela: a resposta pode levar alguns segundos.
 */
object Enderecos {
    private const val ARQUIVO = "destinos.tsv"
    private const val MAXIMO_GUARDADOS = 2000
    /** Meio grau é uns 55 km: pega a cidade e a região metropolitana. */
    private const val RAIO_GRAUS = 0.5
    private val guardados = LinkedHashMap<String, Ponto?>()
    private var carregado = false

    /**
     * O ponto do destino de uma oferta, ou null se não achou (sem internet, endereço desconhecido).
     * [perto] (a casa) tem preferência: a 99 não escreve a cidade, e o mesmo nome existe em outros lugares.
     */
    @Synchronized
    fun destino(ctx: Context, destino: String, perto: Ponto? = null): Ponto? {
        val chave = Casa.consulta(destino)
        carregar(ctx)
        guardados[chave]?.let { return it }
        val p = buscar(ctx, chave, perto)?.first ?: return null
        guardados[chave] = p
        while (guardados.size > MAXIMO_GUARDADOS) guardados.remove(guardados.keys.first())
        runCatching { File(ctx.filesDir, ARQUIVO).appendText("$chave\t${p.lat}\t${p.lon}\n") }
        return p
    }

    /** O ponto e o endereço que o mapa entendeu (para o motorista conferir a casa). */
    fun buscar(ctx: Context, texto: String, perto: Ponto? = null): Pair<Ponto, String>? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(ctx, Locale("pt", "BR"))
        // Primeiro a uns 50 km de [perto]; se não achar ali, em qualquer lugar.
        val a = perto?.let { procurar(g, texto, doubleArrayOf(it.lat - RAIO_GRAUS, it.lon - RAIO_GRAUS, it.lat + RAIO_GRAUS, it.lon + RAIO_GRAUS)) }
            ?: procurar(g, texto, null) ?: return null
        return Ponto(a.latitude, a.longitude) to (a.getAddressLine(0) ?: texto)
    }

    /** [caixa]: sul, oeste, norte, leste. */
    private fun procurar(g: Geocoder, texto: String, caixa: DoubleArray?): Address? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            var r: Address? = null
            val pronto = CountDownLatch(1)
            val resposta = object : Geocoder.GeocodeListener {
                override fun onGeocode(lista: MutableList<Address>) { r = lista.firstOrNull(); pronto.countDown() }
                override fun onError(erro: String?) { pronto.countDown() }
            }
            if (caixa == null) g.getFromLocationName(texto, 1, resposta)
            else g.getFromLocationName(texto, 1, caixa[0], caixa[1], caixa[2], caixa[3], resposta)
            pronto.await(8, TimeUnit.SECONDS)
            r
        } else {
            @Suppress("DEPRECATION")
            (if (caixa == null) g.getFromLocationName(texto, 1)
            else g.getFromLocationName(texto, 1, caixa[0], caixa[1], caixa[2], caixa[3]))?.firstOrNull()
        }
    }.getOrNull()?.takeIf { it.hasLatitude() && it.hasLongitude() }

    /** Rua, número e bairro de um ponto (o "📍 Aqui" do recibo). */
    fun endereco(ctx: Context, p: Ponto): String? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(ctx, Locale("pt", "BR"))
        val a: Address = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                var r: Address? = null
                val pronto = CountDownLatch(1)
                g.getFromLocation(p.lat, p.lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(lista: MutableList<Address>) { r = lista.firstOrNull(); pronto.countDown() }
                    override fun onError(erro: String?) { pronto.countDown() }
                })
                pronto.await(8, TimeUnit.SECONDS)
                r
            } else {
                @Suppress("DEPRECATION")
                g.getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
            }
        }.getOrNull() ?: return null
        val rua = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifBlank { null }
        return listOfNotNull(rua, a.subLocality).joinToString(" - ").ifBlank { a.getAddressLine(0) }
    }

    private fun carregar(ctx: Context) {
        if (carregado) return
        carregado = true
        val f = File(ctx.filesDir, ARQUIVO)
        if (!f.exists()) return
        f.readLines().forEach { l ->
            val c = l.split('\t')
            val lat = c.getOrNull(1)?.toDoubleOrNull()
            val lon = c.getOrNull(2)?.toDoubleOrNull()
            if (lat != null && lon != null) guardados[c[0]] = Ponto(lat, lon)
        }
    }
}

private val PT = Locale("pt", "BR")
