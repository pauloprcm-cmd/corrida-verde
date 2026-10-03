package app.corridaverde

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cfg = Config.carregar(this)
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
        campo(R.id.casaEndereco, EditText::class.java).setText(cfg.casaEndereco)
        campo(R.id.raioCasa, EditText::class.java).setText(cfg.raioCasaKm.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') })
        campo(R.id.salvarCasa, Button::class.java).setOnClickListener { salvarCasa() }
        campo(R.id.indoPraCasa, Button::class.java).setOnClickListener {
            CasaReceiver.alternar(this)
            mostrarCasa()
            Notificacao.atualizar(this)
        }

        campo(R.id.ativar, Button::class.java).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        campo(R.id.ajudaAtivar, Button::class.java).setOnClickListener { ajudaAtivar() }
        campo(R.id.salvar, Button::class.java).setOnClickListener {
            lerCampos()?.let {
                it.salvar(this)
                Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
                // Pede agora, e não no meio de uma corrida.
                if (it.gravar && !GravarActivity.permitido(this)) requestPermissions(GravarActivity.PERMISSOES, 2)
                Notificacao.atualizar(this)
            }
        }
        campo(R.id.testar, Button::class.java).setOnClickListener {
            lerCampos()?.salvar(this) ?: return@setOnClickListener
            val servico = LeitorService.instancia
            if (servico == null) {
                Toast.makeText(this, "Ative a leitura primeiro", Toast.LENGTH_LONG).show()
            } else {
                servico.testar()
            }
        }
        campo(R.id.testarRadar, Button::class.java).setOnClickListener {
            lerCampos()?.salvar(this) ?: return@setOnClickListener
            val servico = LeitorService.instancia
            if (servico == null) {
                Toast.makeText(this, "Ative a leitura primeiro", Toast.LENGTH_LONG).show()
            } else {
                servico.testarRadar()
            }
        }
        campo(R.id.registrarGasto, Button::class.java).setOnClickListener {
            startActivity(Intent(this, GastoActivity::class.java))
        }
        campo(R.id.cobrarPix, Button::class.java).setOnClickListener { startActivity(PixActivity.abrir(this)) }
        campo(R.id.fazerRecibo, Button::class.java).setOnClickListener { startActivity(ReciboActivity.porVoz(this)) }
        campo(R.id.recibosEnviados, Button::class.java).setOnClickListener { startActivity(ReciboActivity.lista(this)) }
        campo(R.id.personalizarRecibo, Button::class.java).setOnClickListener { startActivity(Intent(this, PersonalizarReciboActivity::class.java)) }
        val abrirDinheiro = { _: android.view.View -> startActivity(Intent(this, DinheiroActivity::class.java)) }
        campo(R.id.verDinheiro, Button::class.java).setOnClickListener(abrirDinheiro)
        findViewById<android.view.View>(R.id.cartaoHoje).setOnClickListener(abrirDinheiro)
        // A notificação com o botão "Registrar por voz" precisa desta permissão no Android 13 ou mais novo.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        campo(R.id.compartilhar, Button::class.java).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, diagnostico())
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

    /** Passo a passo para ligar a leitura, com atalho para as duas telas do Android que ela usa. */
    private fun ajudaAtivar() {
        AlertDialog.Builder(this)
            .setTitle("Como ativar a leitura")
            .setMessage(
                "1. Toque em Ativar leitura. Abre a tela Acessibilidade do Android.\n\n" +
                    "2. Procure Apps instalados (em alguns celulares: Serviços instalados ou Aplicativos baixados).\n\n" +
                    "3. Toque em Corrida Verde e ligue a chave.\n\n" +
                    "4. O Android avisa que o app pode ver a tela. Toque em Permitir. O Corrida Verde só lê a oferta e o aviso de radar: não toca em nada.\n\n" +
                    "5. Volte para este app. No topo deve aparecer ✅ Leitura ativa.\n\n" +
                    "Apareceu \"Configuração restrita\" e a chave não liga?\n" +
                    "Isso acontece com apps instalados fora da Play Store. Toque em Liberar configuração abaixo, depois no ⋮ (canto de cima, à direita) e em Permitir configurações restritas. Confirme com o PIN ou a digital e repita os passos de 1 a 4."
            )
            .setPositiveButton("Ir para Acessibilidade") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNeutralButton("Liberar configuração") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val ativo = LeitorService.instancia != null
        campo(R.id.status, TextView::class.java).text =
            if (ativo) "✅ Leitura ativa" else "❌ Leitura desligada: toque em \"Ativar leitura\""
        campo(R.id.textoDiagnostico, TextView::class.java).text = diagnostico()
        mostrarHoje()
        mostrarCasa()
        Notificacao.atualizar(this)

        val versao = campo(R.id.versao, TextView::class.java)
        versao.text = "Versão ${Atualizador.versaoAtual}"
        Atualizador.verificar(this, perguntar = true) { runOnUiThread { versao.text = it } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notificacao.atualizar(this)
    }

    private fun mostrarCasa() {
        val cfg = Config.carregar(this)
        campo(R.id.casaStatus, TextView::class.java).text =
            if (cfg.casa == null) "Casa ainda não cadastrada." else "✅ Casa cadastrada"
        campo(R.id.indoPraCasa, Button::class.java).apply {
            isEnabled = cfg.casa != null
            text = if (cfg.indoPraCasa) "🏠 Indo pra casa: LIGADO (toque para desligar)" else "🏠 Ligar Indo pra casa"
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
                mostrarCasa()
                status.text = "✅ Casa: ${achado.second}\nSe não for a sua rua, corrija o endereço e salve de novo."
                Notificacao.atualizar(this)
            }
        }.start()
    }

    /** O quadro "Sobrou hoje" no topo e, embaixo, ganhos por app e o último consumo. */
    private fun mostrarHoje() {
        val b = Dinheiro.hoje(this)
        campo(R.id.sobrouHoje, TextView::class.java).apply {
            text = Dinheiro.rs(b.sobrou)
            setTextColor(if (b.sobrou < 0) COR_VERMELHO else COR_VERDE)
        }
        campo(R.id.detalheHoje, TextView::class.java).text = "Entrou ${Dinheiro.rs(b.entrou)} · Gastou ${Dinheiro.rs(b.gastou)}" +
            if (b.porApp.isNotEmpty()) "\n" + b.porApp.entries.joinToString(" · ") { "${it.key} ${Dinheiro.rs(it.value)}" } else ""
        val c = Gastos.consumo(Gastos.todos(this))
        campo(R.id.gastosHoje, TextView::class.java).text = c?.let {
            "Último consumo: ${String.format(Locale("pt", "BR"), "%.1f", it.kmPorLitro)} km/${it.unidade.lowercase()} · R$ ${Popup.br(it.custoKm)}/km"
        } ?: ""
    }

    private fun diagnostico(): String {
        val status = File(filesDir, LeitorService.ARQUIVO_STATUS).takeIf { it.exists() }?.readText() ?: ""
        val f = File(filesDir, LeitorService.ARQUIVO_DIAGNOSTICO)
        val leitura = if (f.exists()) f.readText() else "Nenhuma leitura com R$ ainda. Ligue o modo diagnóstico e abra uma oferta na Uber."
        return status + "\n" + leitura
    }

    private fun lerCampos(): Config? {
        fun num(id: Int) = campo(id, EditText::class.java).text.toString().trim().replace(',', '.').toDoubleOrNull()
        val verde = num(R.id.limiteVerde)?.toInt()
        val amarelo = num(R.id.limiteAmarelo)?.toInt()
        val busca = num(R.id.buscaMax)
        val nota = num(R.id.notaMinima)
        val viagem = num(R.id.viagemLonga)
        val y = num(R.id.posicaoY)?.toInt()
        if (verde == null || amarelo == null || busca == null || nota == null || viagem == null || y == null || amarelo > verde) {
            Toast.makeText(this, "Confira os números (o amarelo não pode passar do verde)", Toast.LENGTH_LONG).show()
            return null
        }
        // Parte com o que não está nestes campos (a casa e o modo Indo pra casa).
        return Config.carregar(this).copy(
            luxo = campo(R.id.luxo, RadioButton::class.java).isChecked,
            limiteVerde = verde,
            limiteAmarelo = amarelo,
            buscaMaxKm = busca,
            notaMinima = nota,
            viagemLongaKm = viagem,
            posicaoY = y,
            diagnostico = campo(R.id.diagnostico, CheckBox::class.java).isChecked,
            gravar = campo(R.id.gravar, CheckBox::class.java).isChecked,
            radar = campo(R.id.radar, CheckBox::class.java).isChecked,
            radarSom = campo(R.id.radarSom, CheckBox::class.java).isChecked,
        )
    }

    private companion object {
        val COR_VERDE = android.graphics.Color.rgb(0x1B, 0x8A, 0x3C)
        val COR_VERMELHO = android.graphics.Color.rgb(0xC6, 0x28, 0x28)
    }

    private fun <T : android.view.View> campo(id: Int, tipo: Class<T>): T = tipo.cast(findViewById(id))!!
}
