package app.corridaverde

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import java.text.Normalizer

/**
 * Lista de "Pendências" do app da cooperativa (Taxi Digital, da USE TAXI). Cada pedido vem numa linha assim:
 * "PA 4B QTR(07:50) MELIA IBIRAPUERA - AVENIDA IBIRAPUERA, 2534 Dist.3.981 mts /// QTI Area 5A /// WPET".
 * PA é a área do embarque, QTR a hora, Dist. a distância em linha reta até o embarque e QTI Area a área do destino.
 * O app só diz a área do destino: o Corrida Verde escreve o bairro dela e os km aproximados do embarque até lá.
 * Fica fora dos textos do app de propósito: só quem usa a cooperativa vê (Paulo, 12/11/2026).
 */
object Pendencias {
    const val PACOTE = "br.com.taxidigital"
    /** Linha reta até o centro do bairro, mais as curvas do caminho. */
    private const val FATOR_CAMINHO = 1.3
    /** O endereço do embarque que o mapa achar mais longe que isso do centro do PA é outro lugar com o mesmo nome. */
    private const val LONGE_DO_PA_KM = 15.0

    data class Area(val nome: String, val ponto: Ponto)
    data class Pedido(val pa: Area?, val endereco: String?, val destino: Area)

    private val PEDIDO = Regex("""^PA\s+(.+?)\s+Dist\.?\s*[\d.,]+\s*mts\b.*?QTI\s+Area\s+([^/]+?)\s*(?:///|$)""", RegexOption.IGNORE_CASE)
    private val QTR = Regex("""^QTR\s*\([^)]*\)\s*""", RegexOption.IGNORE_CASE)
    private val COM_NUMERO = Regex(""",\s*\d""")

    /** Null se o texto não é um pedido ou se a área do destino não está na lista. */
    fun ler(texto: String): Pedido? {
        val t = texto.replace(Regex("""\s+"""), " ").trim()
        val m = PEDIDO.find(t) ?: return null
        val destino = area(m.groupValues[2]) ?: return null
        val inicio = m.groupValues[1]
        val chave = chave(inicio)
        // O código do PA pode ter espaço ("3A PTO", "STA A"): vale o mais comprido que bate com o começo.
        val codigo = CODIGOS.filter { chave == it || chave.startsWith("$it ") }.maxByOrNull { it.length }
        val resto = if (codigo == null) inicio else inicio.drop(codigo.length).trim()
        val lugar = QTR.replace(resto, "").trim()
        // "MELIA IBIRAPUERA - AVENIDA IBIRAPUERA, 2534": para o mapa vale a parte da rua.
        val endereco = lugar.split(" - ").lastOrNull { COM_NUMERO.containsMatchIn(it) } ?: lugar.takeIf { COM_NUMERO.containsMatchIn(it) }
        return Pedido(codigo?.let { AREAS[it] }, endereco?.trim(), destino)
    }

    fun area(codigo: String): Area? {
        var c = chave(codigo)
        if (c.length == 1 && c[0].isDigit()) c = "0$c"
        return AREAS[c]
    }

    /** A etiqueta do fim da linha. [embarque] é o ponto do endereço, se o mapa já achou; senão vale o centro do PA. */
    fun etiqueta(p: Pedido, embarque: Ponto?): String {
        val de = embarque ?: p.pa?.ponto ?: return "→ ${p.destino.nome}"
        val km = Math.round(Casa.distanciaKm(de, p.destino.ponto) * FATOR_CAMINHO).coerceAtLeast(1)
        return "→ ${p.destino.nome} · ~$km km"
    }

    /** O ponto do endereço do embarque pela internet (guardado no celular depois da primeira vez). Fora da linha da tela. */
    fun pontoDoEmbarque(ctx: Context, p: Pedido): Ponto? {
        val endereco = p.endereco ?: return null
        val perto = p.pa?.ponto
        val achado = Enderecos.destino(ctx, endereco, perto) ?: return null
        return achado.takeIf { perto == null || Casa.distanciaKm(it, perto) <= LONGE_DO_PA_KM }
    }

    private fun chave(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
        .uppercase().replace(Regex("""\s+"""), " ").trim()

    private val AREAS: Map<String, Area> by lazy { LISTA.associate { (c, a) -> chave(c) to a } }
    private val CODIGOS by lazy { AREAS.keys }

    /** A lista de PAs da USE TAXI (Print/PA's da USETAXI), com o centro de cada um pelo OpenStreetMap. */
    private val LISTA: List<Pair<String, Area>> = listOf(
        "01" to Area("Paraíso", Ponto(-23.5754, -46.6410)),
        "1A" to Area("Ibirapuera", Ponto(-23.5877, -46.6585)),
        "1B" to Area("Jd. Lusitânia", Ponto(-23.5890, -46.6500)),
        "02" to Area("Pacaembu", Ponto(-23.5484, -46.6679)),
        "2A" to Area("Sumaré", Ponto(-23.5510, -46.6776)),
        "2B" to Area("Clínicas", Ponto(-23.5570, -46.6700)),
        "2C" to Area("Perdizes", Ponto(-23.5400, -46.6710)),
        "03" to Area("Barata Ribeiro", Ponto(-23.5565, -46.6533)),
        "3A" to Area("Santa Cecília", Ponto(-23.5297, -46.6519)),
        "3A PTO" to Area("Rua Jaguaribe", Ponto(-23.5435, -46.6490)),
        "04" to Area("Moema", Ponto(-23.5971, -46.6629)),
        "4A" to Area("Vila Uberabinha", Ponto(-23.6097, -46.6642)),
        "4B" to Area("Indianópolis", Ponto(-23.6075, -46.6689)),
        "PONTO TABAPUA" to Area("Itaim", Ponto(-23.5857, -46.6853)),
        "05" to Area("R. Salvador Cardoso", Ponto(-23.5871, -46.6856)),
        "5A" to Area("Vila Olímpia", Ponto(-23.5940, -46.6842)),
        "5B" to Area("Itaim", Ponto(-23.5844, -46.6784)),
        "5C" to Area("Vila Nova Conceição", Ponto(-23.5900, -46.6700)),
        "5A PTO" to Area("Rua Tenerife", Ponto(-23.5964, -46.6880)),
        "06" to Area("Cidade Monções", Ponto(-23.6060, -46.6930)),
        "6A" to Area("Brooklin Paulista", Ponto(-23.6294, -46.6737)),
        "07" to Area("Liberdade", Ponto(-23.5666, -46.6319)),
        "7A" to Area("Cambuci", Ponto(-23.5659, -46.6137)),
        "7B" to Area("Aclimação", Ponto(-23.5671, -46.6268)),
        "08" to Area("Congonhas", Ponto(-23.6274, -46.6556)),
        "8A" to Area("Brooklin Velho", Ponto(-23.6271, -46.6801)),
        "09" to Area("Praça Panamericana", Ponto(-23.5538, -46.7089)),
        "9A" to Area("Largo da Batata", Ponto(-23.5661, -46.6940)),
        "9B" to Area("Vila Madalena", Ponto(-23.5465, -46.6911)),
        "9C" to Area("Pinheiros", Ponto(-23.5672, -46.7020)),
        "10" to Area("Mooca", Ponto(-23.5607, -46.5972)),
        "10A" to Area("Alto da Mooca", Ponto(-23.5712, -46.5812)),
        "11" to Area("Morumbi", Ponto(-23.5974, -46.7068)),
        "11A" to Area("Paraisópolis", Ponto(-23.6153, -46.7258)),
        "11B" to Area("Vila Sônia", Ponto(-23.5987, -46.7393)),
        "12" to Area("Butantã", Ponto(-23.5694, -46.7278)),
        "12A" to Area("Jardim Bonfiglioli", Ponto(-23.5760, -46.7400)),
        "12B" to Area("Raposo Tavares", Ponto(-23.5917, -46.7805)),
        "13" to Area("Pirituba", Ponto(-23.4855, -46.7219)),
        "13A" to Area("Taipas", Ponto(-23.4433, -46.7149)),
        "13B" to Area("Freguesia do Ó", Ponto(-23.4875, -46.6951)),
        "13C" to Area("Damasceno", Ponto(-23.4527, -46.6974)),
        "14" to Area("Limão", Ponto(-23.5033, -46.6753)),
        "14A" to Area("Jardim Peri", Ponto(-23.4571, -46.6669)),
        "14B" to Area("Casa Verde", Ponto(-23.5059, -46.6561)),
        "15" to Area("Vila Guilherme", Ponto(-23.5171, -46.6080)),
        "15A" to Area("Vila Maria", Ponto(-23.5132, -46.5892)),
        "15B" to Area("Vila Medeiros", Ponto(-23.4877, -46.5845)),
        "15C" to Area("Tremembé", Ponto(-23.4483, -46.6030)),
        "15D" to Area("Água Fria", Ponto(-23.4818, -46.6250)),
        "16" to Area("Ipiranga", Ponto(-23.5893, -46.6062)),
        "16A" to Area("Bosque da Saúde", Ponto(-23.6094, -46.6252)),
        "17" to Area("Cenesp", Ponto(-23.6380, -46.7300)),
        "17A" to Area("Vila Andrade", Ponto(-23.6257, -46.7270)),
        "18" to Area("Chácara Flora", Ponto(-23.6468, -46.6854)),
        "18A" to Area("Jurubatuba", Ponto(-23.6720, -46.7030)),
        "18B" to Area("Vila Sofia", Ponto(-23.6630, -46.6930)),
        "19" to Area("Vila Romana", Ponto(-23.5288, -46.7037)),
        "19A" to Area("Água Branca", Ponto(-23.5213, -46.6889)),
        "19B" to Area("Lapa", Ponto(-23.5216, -46.7043)),
        "19C" to Area("Alto da Lapa", Ponto(-23.5274, -46.7131)),
        "19D" to Area("Vila Leopoldina", Ponto(-23.5275, -46.7329)),
        "20" to Area("Shopping Morumbi", Ponto(-23.6234, -46.6985)),
        "20A" to Area("Santo Amaro", Ponto(-23.6562, -46.7191)),
        "20B" to Area("Granja Julieta", Ponto(-23.6275, -46.7120)),
        "20C" to Area("Parque da Cidade", Ponto(-23.6250, -46.7047)),
        "20E" to Area("Parque da Cidade", Ponto(-23.6250, -46.7047)),
        "20S" to Area("Parque da Cidade", Ponto(-23.6250, -46.7047)),
        "21" to Area("Bom Retiro", Ponto(-23.5252, -46.6382)),
        "SALA SP" to Area("Rua Mauá, 51", Ponto(-23.5343, -46.6390)),
        "22" to Area("Jabaquara", Ponto(-23.6487, -46.6455)),
        "22A" to Area("Vila Campestre", Ponto(-23.6597, -46.6484)),
        "22B" to Area("Santa Catarina", Ponto(-23.6517, -46.6570)),
        "23" to Area("Parque Bristol", Ponto(-23.6436, -46.6083)),
        "23A" to Area("São João Clímaco", Ponto(-23.6243, -46.5942)),
        "23B" to Area("Vila Moraes", Ponto(-23.6280, -46.6090)),
        "24" to Area("Saúde", Ponto(-23.6152, -46.6434)),
        "24A" to Area("Mirandópolis", Ponto(-23.6083, -46.6424)),
        "25" to Area("Vila Ema", Ponto(-23.5801, -46.5617)),
        "25A" to Area("Vila Industrial", Ponto(-23.5950, -46.5450)),
        "25B" to Area("Vila Alpina", Ponto(-23.6524, -46.5417)),
        "26" to Area("Brás", Ponto(-23.5451, -46.6163)),
        "26A" to Area("Belenzinho", Ponto(-23.5450, -46.5844)),
        "26B" to Area("Pari", Ponto(-23.5330, -46.6158)),
        "27" to Area("Vila Gomes Cardim", Ponto(-23.5452, -46.5617)),
        "27A" to Area("Vila Formosa", Ponto(-23.5669, -46.5463)),
        "27B" to Area("Vila Carrão", Ponto(-23.5499, -46.5386)),
        "27C" to Area("Tatuapé", Ponto(-23.5403, -46.5766)),
        "28" to Area("Jardim Aricanduva", Ponto(-23.5686, -46.5090)),
        "28A" to Area("Jardim Sapopemba", Ponto(-23.6043, -46.5099)),
        "28B" to Area("Jardim Conquista", Ponto(-23.6074, -46.4481)),
        "28C" to Area("Cidade São Mateus", Ponto(-23.5983, -46.4817)),
        "29" to Area("Vila Matilde", Ponto(-23.5362, -46.5246)),
        "29A" to Area("Vila Carmosina", Ponto(-23.5404, -46.4542)),
        "29B" to Area("Artur Alvim", Ponto(-23.5402, -46.4847)),
        "29C" to Area("Parque Aricanduva", Ponto(-23.5360, -46.5488)),
        "29D" to Area("Cidade Patriarca", Ponto(-23.5326, -46.5042)),
        "29E" to Area("Vila Matilde", Ponto(-23.5362, -46.5246)),
        "AERO" to Area("Aeroporto de Guarulhos", Ponto(-23.4379, -46.4806)),
        "30" to Area("Tatuapé", Ponto(-23.5403, -46.5766)),
        "30A" to Area("Cangaíba", Ponto(-23.5059, -46.5314)),
        "30B" to Area("Ponte Rasa", Ponto(-23.5110, -46.4871)),
        "31" to Area("José Bonifácio", Ponto(-23.5583, -46.4381)),
        "31A" to Area("Cidade Tiradentes", Ponto(-23.5802, -46.3971)),
        "31B" to Area("Guaianases", Ponto(-23.5523, -46.4070)),
        "32" to Area("Itaquera", Ponto(-23.5423, -46.4712)),
        "32A" to Area("Lajeado", Ponto(-23.5349, -46.4089)),
        "32B" to Area("São Miguel", Ponto(-23.4949, -46.4391)),
        "32C" to Area("Itaim Paulista", Ponto(-23.5018, -46.3996)),
        "33" to Area("Socorro", Ponto(-23.6846, -46.7117)),
        "33A" to Area("Capão Redondo", Ponto(-23.6719, -46.7794)),
        "33B" to Area("Jardim Ângela", Ponto(-23.7125, -46.7687)),
        "33C" to Area("Valo Velho", Ponto(-23.6900, -46.7920)),
        "34" to Area("Veleiros", Ponto(-23.6930, -46.6980)),
        "34A" to Area("Interlagos", Ponto(-23.7018, -46.6969)),
        "34B" to Area("Grajaú", Ponto(-23.7800, -46.6738)),
        "35" to Area("Cupecê", Ponto(-23.6662, -46.6564)),
        "35A" to Area("Americanópolis", Ponto(-23.6759, -46.6496)),
        "35B" to Area("Campo Grande", Ponto(-23.6722, -46.6846)),
        "35C" to Area("Eldorado", Ponto(-23.7259, -46.6214)),
        "36" to Area("Jaguaré", Ponto(-23.5492, -46.7441)),
        "36A" to Area("Parque dos Príncipes", Ponto(-23.5757, -46.7710)),
        "37" to Area("Vila Mariana", Ponto(-23.5837, -46.6327)),
        "37A" to Area("Vila Clementino", Ponto(-23.5980, -46.6434)),
        "38" to Area("Bela Vista", Ponto(-23.5601, -46.6500)),
        "38A" to Area("Sé", Ponto(-23.5504, -46.6331)),
        "38B" to Area("Praça do Correio", Ponto(-23.5434, -46.6361)),
        "38C" to Area("Bela Vista", Ponto(-23.5601, -46.6500)),
        "39" to Area("Jardim América", Ponto(-23.5696, -46.6720)),
        "39A" to Area("Cerqueira César", Ponto(-23.5610, -46.6658)),
        "40" to Area("Shopping Eldorado", Ponto(-23.5726, -46.6958)),
        "40A" to Area("Jardim Europa", Ponto(-23.5737, -46.6823)),
        "41" to Area("Fórum Trabalhista", Ponto(-23.5205, -46.6720)),
        "41A" to Area("Barra Funda", Ponto(-23.5255, -46.6675)),
        "42" to Area("Jardim da Glória", Ponto(-23.5827, -46.6220)),
        "42A" to Area("Cursino", Ponto(-23.6276, -46.6193)),
        "PONTO 43" to Area("Hyatt", Ponto(-23.6165, -46.6976)),
        "43" to Area("Hyatt", Ponto(-23.6165, -46.6976)),
        "43 HIAE" to Area("Einstein (Morumbi)", Ponto(-23.5996, -46.7149)),
        "44" to Area("Parque São Domingos", Ponto(-23.4995, -46.7381)),
        "44A" to Area("Jaguara", Ponto(-23.5138, -46.7472)),
        "44B" to Area("Mutinga", Ponto(-23.4911, -46.7507)),
        "45" to Area("Aeroporto", Ponto(-23.6274, -46.6556)),
        "PL GOL" to Area("Aeroporto", Ponto(-23.6274, -46.6556)),
        "BOLSÃO" to Area("Aeroporto", Ponto(-23.6274, -46.6556)),
        "STA" to Area("Santo André", Ponto(-23.6534, -46.5279)),
        "STA A" to Area("Utinga (Santo André)", Ponto(-23.6262, -46.5441)),
        "STA B" to Area("Vila América (Sto. André)", Ponto(-23.6677, -46.5085)),
        "SBC" to Area("São Bernardo", Ponto(-23.7080, -46.5507)),
        "SBC A" to Area("Demarchi (SBC)", Ponto(-23.7395, -46.5559)),
        "SCS" to Area("São Caetano", Ponto(-23.6196, -46.5688)),
        "DIA" to Area("Diadema", Ponto(-23.6866, -46.6247)),
        "MAU" to Area("Mauá", Ponto(-23.6670, -46.4617)),
        "RIP" to Area("Ribeirão Pires", Ponto(-23.7134, -46.4161)),
        "RGS" to Area("Rio Grande da Serra", Ponto(-23.7442, -46.3925)),
        "OSA" to Area("Osasco", Ponto(-23.5325, -46.7917)),
        "OSA A" to Area("Santo Antônio (Osasco)", Ponto(-23.5568, -46.7999)),
        "BAR" to Area("Barueri", Ponto(-23.5112, -46.8765)),
        "CRP" to Area("Carapicuíba", Ponto(-23.5235, -46.8407)),
        "JAN" to Area("Jandira", Ponto(-23.5282, -46.9020)),
        "ITV" to Area("Itapevi", Ponto(-23.5513, -46.9598)),
        "TBS" to Area("Taboão da Serra", Ponto(-23.6122, -46.7791)),
        "COT" to Area("Cotia", Ponto(-23.6039, -46.9189)),
        "EMB" to Area("Embu das Artes", Ponto(-23.6495, -46.8528)),
        "VGD" to Area("Vargem Grande Paulista", Ponto(-23.5993, -47.0220)),
        "FCO" to Area("Francisco Morato", Ponto(-23.2817, -46.7449)),
        "MRP" to Area("Mairiporã", Ponto(-23.3190, -46.5876)),
        "GRU" to Area("Bonsucesso (Guarulhos)", Ponto(-23.4112, -46.4039)),
        "GRU A" to Area("Capoava", Ponto(-23.4237, -46.4070)),
        "POA" to Area("Poá", Ponto(-23.5249, -46.3446)),
        "MOG" to Area("Mogi das Cruzes", Ponto(-23.5234, -46.1927)),
        "ARJ" to Area("Arujá", Ponto(-23.3963, -46.3175)),
        "GUARAREMA" to Area("Guararema", Ponto(-23.4137, -46.0384)),
        "SUZ" to Area("Suzano", Ponto(-23.5428, -46.3108)),
        "BERTIOGA" to Area("Bertioga", Ponto(-23.8486, -46.1396)),
        "BIRIGUI" to Area("Birigui", Ponto(-21.2908, -50.3414)),
        "REGISTRO" to Area("Registro", Ponto(-24.4877, -47.8436)),
        "RJ 1" to Area("Rio de Janeiro", Ponto(-22.9110, -43.2094)),
        "RJ 2" to Area("Rio de Janeiro", Ponto(-22.9110, -43.2094)),
        "PIRACICABA" to Area("Piracicaba", Ponto(-22.7252, -47.6493)),
        "SÃO PEDRO" to Area("São Pedro", Ponto(-22.5513, -47.9072)),
    )
}

/** As etiquetas por cima da lista, uma no canto de baixo à direita de cada pedido. Não recebem toques. */
class EtiquetasPendencias(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var tela: FrameLayout? = null
    private val etiquetas = mutableListOf<TextView>()
    val visivel get() = tela != null

    /** Só na linha da tela. [itens]: o retângulo de cada pedido na tela e o texto da etiqueta. */
    fun mostrar(itens: List<Pair<Rect, String>>) {
        if (itens.isEmpty()) return esconder()
        val t = tela ?: criar()
        while (etiquetas.size < itens.size) etiquetas += novaEtiqueta().also { t.addView(it) }
        val origem = IntArray(2).also { t.getLocationOnScreen(it) }
        etiquetas.forEachIndexed { i, v ->
            val item = itens.getOrNull(i)
            if (item == null) { v.visibility = View.GONE; return@forEachIndexed }
            val (r, texto) = item
            v.text = texto
            v.visibility = View.VISIBLE
            v.measure(View.MeasureSpec.makeMeasureSpec(r.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
            v.translationX = (r.right - v.measuredWidth - dp(4) - origem[0]).toFloat()
            v.translationY = (r.bottom - v.measuredHeight - origem[1]).toFloat()
        }
    }

    private fun criar(): FrameLayout {
        val t = FrameLayout(ctx)
        wm.addView(t, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START })
        tela = t
        return t
    }

    private fun novaEtiqueta() = TextView(ctx).apply {
        setTextColor(Color.rgb(0x2E, 0xC4, 0x4F))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        setPadding(dp(8), dp(2), dp(8), dp(3))
        background = GradientDrawable().apply {
            setColor(Color.argb(235, 0, 0, 0))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(2), Color.rgb(0x2E, 0xC4, 0x4F))
        }
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    }

    fun esconder() {
        tela?.let { runCatching { wm.removeView(it) } }
        tela = null
        etiquetas.clear()
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
