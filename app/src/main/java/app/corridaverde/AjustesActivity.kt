package app.corridaverde

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Os ajustes do aviso, do radar, da casa e da gravação, e o diagnóstico (em Avançado). */
class AjustesActivity : Activity() {
    private var restaurando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ajustes)
        findViewById<LinearLayout>(R.id.coluna).addView(Visual(this).topo("Ajustes"), 0)
        listOf(R.id.buscaMax, R.id.notaMinima, R.id.viagemLonga, R.id.raioCasa, *CAMPOS_APP).forEach { findViewById<EditText>(it).aceitarVirgula() }

        val cfg = Config.carregar(this)
        campo(R.id.perfilApp, RadioButton::class.java).isChecked = cfg.motoristaDeApp
        campo(R.id.perfilTaxi, RadioButton::class.java).isChecked = !cfg.motoristaDeApp
        mostrarPerfil(cfg.motoristaDeApp)
        campo(R.id.perfil, RadioGroup::class.java).setOnCheckedChangeListener { _, id -> mostrarPerfil(id == R.id.perfilApp) }
        listOf(cfg.verdeEconomico, cfg.amareloEconomico, cfg.verdeConforto, cfg.amareloConforto, cfg.verdePremium, cfg.amareloPremium)
            .zip(CAMPOS_APP.toList()).forEach { (valor, id) -> campo(id, EditText::class.java).setText(Popup.br(valor)) }
        campo(R.id.luxo, RadioButton::class.java).isChecked = cfg.luxo
        campo(R.id.comum, RadioButton::class.java).isChecked = !cfg.luxo
        campo(R.id.limiteVerde, EditText::class.java).setText(cfg.limiteVerde.toString())
        campo(R.id.limiteAmarelo, EditText::class.java).setText(cfg.limiteAmarelo.toString())
        campo(R.id.buscaMax, EditText::class.java).setText(cfg.buscaMaxKm.toString().replace('.', ','))
        campo(R.id.notaMinima, EditText::class.java).setText(cfg.notaMinima.toString().replace('.', ','))
        campo(R.id.viagemLonga, EditText::class.java).setText(cfg.viagemLongaKm.toString().replace('.', ','))
        campo(R.id.posicaoY, EditText::class.java).setText(cfg.posicaoY.toString())
        campo(R.id.diagnostico, CheckBox::class.java).isChecked = cfg.diagnostico
        campo(R.id.gravar, CheckBox::class.java).isChecked = cfg.gravar
        campo(R.id.radar, CheckBox::class.java).isChecked = cfg.radar
        campo(R.id.radarSom, CheckBox::class.java).isChecked = cfg.radarSom
        campo(R.id.aviso99, CheckBox::class.java).apply {
            isChecked = cfg.aviso99
            if (Build.VERSION.SDK_INT < 30) {
                isEnabled = false
                text = Celular.semAviso99
            }
        }
        campo(R.id.bolinha, CheckBox::class.java).isChecked = cfg.bolinha
        campo(R.id.casaEndereco, EditText::class.java).setText(cfg.casaEndereco)
        campo(R.id.raioCasa, EditText::class.java).setText(cfg.raioCasaKm.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') })
        campo(R.id.salvarCasa, Button::class.java).setOnClickListener { salvarCasa() }

        campo(R.id.ativar, Button::class.java).setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        campo(R.id.salvar, Button::class.java).setOnClickListener {
            lerCampos()?.let {
                it.salvar(this)
                Toast.makeText(this, "Ajustes salvos", Toast.LENGTH_SHORT).show()
                // Pede agora, e não no meio de uma corrida.
                if (it.gravar && !GravarActivity.permitido(this)) requestPermissions(GravarActivity.PERMISSOES, 2)
                Notificacao.atualizar(this)
            }
        }
        campo(R.id.testar, Button::class.java).setOnClickListener {
            lerCampos()?.salvar(this) ?: return@setOnClickListener
            LeitorService.instancia?.testar() ?: Toast.makeText(this, "Ligue a leitura primeiro (tela inicial)", Toast.LENGTH_LONG).show()
        }
        campo(R.id.testarRadar, Button::class.java).setOnClickListener {
            lerCampos()?.salvar(this) ?: return@setOnClickListener
            LeitorService.instancia?.testarRadar() ?: Toast.makeText(this, "Ligue a leitura primeiro (tela inicial)", Toast.LENGTH_LONG).show()
        }
        campo(R.id.guardarCopia, Button::class.java).setOnClickListener {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE, Copia.nomeDoArquivo())
            runCatching { startActivityForResult(i, GUARDAR) }.onFailure { enviarCopia() }
        }
        campo(R.id.enviarCopia, Button::class.java).setOnClickListener { enviarCopia() }
        campo(R.id.restaurarCopia, Button::class.java).setOnClickListener {
            // "*/*": o WhatsApp e o Drive nem sempre dizem que o arquivo é um zip.
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), RESTAURAR)
        }
        campo(R.id.compartilhar, Button::class.java).setOnClickListener {
            // Com o print da oferta da 99 (teste da leitura por imagem), a imagem vai junto com o texto.
            val print = File(ArquivosProvider.pasta(this), LeitorService.PRINT_99).takeIf { it.isFile }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, diagnostico())
                print?.let {
                    val uri = ArquivosProvider.uri(it)
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }, "Enviar diagnóstico"))
        }
        campo(R.id.limparDiagnostico, Button::class.java).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Apagar tudo o que o diagnóstico juntou até agora?")
                .setPositiveButton("Limpar") { _, _ ->
                    LeitorService.instancia?.limparDiagnostico() ?: LeitorService.apagarArquivosDoDiagnostico(this)
                    campo(R.id.textoDiagnostico, TextView::class.java).text = "Diagnóstico limpo."
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        campo(R.id.textoDiagnostico, TextView::class.java).text = diagnostico()
        mostrarCasa()
    }

    /** Ao sair da tela, guarda o que foi mudado, para ninguém perder um ajuste por esquecer o Salvar. */
    override fun onPause() {
        super.onPause()
        // Logo depois de restaurar, os campos ainda mostram os ajustes antigos: não podem sobrescrever a cópia.
        if (restaurando) return
        lerCampos(avisar = false)?.let {
            if (it != Config.carregar(this)) {
                it.salvar(this)
                Notificacao.atualizar(this)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            GUARDAR -> {
                val ok = runCatching { contentResolver.openOutputStream(uri)!!.use { Copia.gerar(this, it) } }.isSuccess
                Toast.makeText(this, if (ok) "Cópia guardada" else "Não deu para guardar a cópia aí. Tente Mandar a cópia.", Toast.LENGTH_LONG).show()
            }
            RESTAURAR -> {
                val resumo = runCatching { contentResolver.openInputStream(uri)!!.use { Copia.conferir(it) } }.getOrNull()
                if (resumo == null) {
                    Toast.makeText(this, "Esse arquivo não é uma cópia do Corrida Verde", Toast.LENGTH_LONG).show()
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("Restaurar a cópia?")
                    .setMessage(
                        "Cópia feita em ${resumo.feitaEm}, com ${resumo.lancamentos} lançamentos de ganhos e gastos e ${resumo.recibos} recibos.\n\n" +
                            "Os dados que estão neste celular agora serão trocados pelos da cópia.",
                    )
                    .setPositiveButton("Restaurar") { _, _ ->
                        val ok = runCatching { contentResolver.openInputStream(uri)!!.use { Copia.restaurar(this, it) } }.getOrDefault(false)
                        if (!ok) {
                            Toast.makeText(this, "Não deu para ler a cópia", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                        Notificacao.atualizar(this)
                        Toast.makeText(this, "Pronto: dados restaurados", Toast.LENGTH_LONG).show()
                        restaurando = true
                        recreate()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    /** Gera a cópia na pasta de envio e abre a lista de apps (WhatsApp, Gmail, Drive…). */
    private fun enviarCopia() {
        val f = java.io.File(ArquivosProvider.pasta(this), Copia.nomeDoArquivo())
        runCatching { f.outputStream().use { Copia.gerar(this, it) } }.onFailure {
            Toast.makeText(this, "Não deu para gerar a cópia", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND).setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, ArquivosProvider.uri(f))
            .putExtra(Intent.EXTRA_SUBJECT, "Cópia dos dados do Corrida Verde")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(i, "Mandar a cópia"))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notificacao.atualizar(this)
    }

    private fun mostrarCasa() {
        val cfg = Config.carregar(this)
        campo(R.id.casaStatus, TextView::class.java).text = when {
            cfg.casa == null -> "Casa ainda não cadastrada."
            cfg.indoPraCasa -> "✅ Casa cadastrada · Indo pra casa LIGADO hoje"
            else -> "✅ Casa cadastrada. Ligue o Indo pra casa no Meu dia."
        }
    }

    /** Procura o endereço no mapa (pela internet) e mostra o que achou, para o motorista conferir. */
    private fun salvarCasa() {
        val endereco = campo(R.id.casaEndereco, EditText::class.java).text.toString().trim()
        val raio = campo(R.id.raioCasa, EditText::class.java).text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (endereco.length < 5 || raio == null || raio <= 0) {
            Toast.makeText(this, "Escreva o endereço e quantos km conta como perto", Toast.LENGTH_LONG).show()
            return
        }
        val status = campo(R.id.casaStatus, TextView::class.java)
        status.text = "Procurando o endereço…"
        Thread {
            val achado = runCatching { Enderecos.buscar(this, Casa.consulta(endereco)) }.getOrNull()
            runOnUiThread {
                if (achado == null) {
                    status.text = "Não achei esse endereço. Confira a internet e escreva com bairro e cidade."
                    return@runOnUiThread
                }
                Config.carregar(this).copy(casaEndereco = endereco, casa = achado.first, raioCasaKm = raio).salvar(this)
                status.text = "✅ Casa: ${achado.second}\nSe não for a sua rua, corrija o endereço e salve de novo."
                Notificacao.atualizar(this)
            }
        }.start()
    }

    private fun diagnostico(): String {
        val status = File(filesDir, LeitorService.ARQUIVO_STATUS).takeIf { it.exists() }?.readText() ?: ""
        val f = File(filesDir, LeitorService.ARQUIVO_DIAGNOSTICO)
        val leitura = if (f.exists()) f.readText() else "Nenhuma leitura com R$ ainda. Ligue o modo diagnóstico e abra uma oferta na Uber."
        return status + "\n" + leitura
    }

    /** Taxista vê o taxímetro e a % ; motorista de app vê os mínimos de R$/km. */
    private fun mostrarPerfil(app: Boolean) {
        campo(R.id.soTaxi, LinearLayout::class.java).visibility = if (app) View.GONE else View.VISIBLE
        campo(R.id.soApp, LinearLayout::class.java).visibility = if (app) View.VISIBLE else View.GONE
    }

    private fun lerCampos(avisar: Boolean = true): Config? {
        fun num(id: Int) = campo(id, EditText::class.java).text.toString().trim().replace(',', '.').toDoubleOrNull()
        val porKm = CAMPOS_APP.map { num(it) }
        if (porKm.any { it == null } || porKm[1]!! > porKm[0]!! || porKm[3]!! > porKm[2]!! || porKm[5]!! > porKm[4]!!) {
            if (avisar) Toast.makeText(this, "Confira os valores por km (o amarelo não pode passar do verde)", Toast.LENGTH_LONG).show()
            return null
        }
        val verde = num(R.id.limiteVerde)?.toInt()
        val amarelo = num(R.id.limiteAmarelo)?.toInt()
        val busca = num(R.id.buscaMax)
        val nota = num(R.id.notaMinima)
        val viagem = num(R.id.viagemLonga)
        val y = num(R.id.posicaoY)?.toInt()
        if (verde == null || amarelo == null || busca == null || nota == null || viagem == null || y == null || amarelo > verde) {
            if (avisar) Toast.makeText(this, "Confira os números (o amarelo não pode passar do verde)", Toast.LENGTH_LONG).show()
            return null
        }
        // Parte com o que não está nestes campos (a casa e o modo Indo pra casa).
        val atual = Config.carregar(this)
        val diagnostico = campo(R.id.diagnostico, CheckBox::class.java).isChecked
        return atual.copy(
            luxo = campo(R.id.luxo, RadioButton::class.java).isChecked,
            limiteVerde = verde,
            limiteAmarelo = amarelo,
            buscaMaxKm = busca,
            notaMinima = nota,
            viagemLongaKm = viagem,
            posicaoY = y,
            diagnostico = diagnostico,
            // Ligou agora: conta as 24 h a partir daqui.
            diagnosticoAte = if (diagnostico && !atual.diagnostico) System.currentTimeMillis() + Config.DURACAO_DIAGNOSTICO else atual.diagnosticoAte,
            gravar = campo(R.id.gravar, CheckBox::class.java).isChecked,
            radar = campo(R.id.radar, CheckBox::class.java).isChecked,
            radarSom = campo(R.id.radarSom, CheckBox::class.java).isChecked,
            aviso99 = campo(R.id.aviso99, CheckBox::class.java).isChecked,
            bolinha = campo(R.id.bolinha, CheckBox::class.java).isChecked,
            perfil = if (campo(R.id.perfilApp, RadioButton::class.java).isChecked) Config.APP else Config.TAXI,
            verdeEconomico = porKm[0]!!,
            amareloEconomico = porKm[1]!!,
            verdeConforto = porKm[2]!!,
            amareloConforto = porKm[3]!!,
            verdePremium = porKm[4]!!,
            amareloPremium = porKm[5]!!,
        )
    }

    private fun <T : android.view.View> campo(id: Int, tipo: Class<T>): T = tipo.cast(findViewById(id))!!

    private companion object {
        val CAMPOS_APP = arrayOf(R.id.verdeEconomico, R.id.amareloEconomico, R.id.verdeConforto, R.id.amareloConforto, R.id.verdePremium, R.id.amareloPremium)
        const val GUARDAR = 10
        const val RESTAURAR = 11
    }
}
