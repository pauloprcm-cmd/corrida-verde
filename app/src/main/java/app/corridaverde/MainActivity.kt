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
import java.time.LocalDate
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
        campo(R.id.lancamentos, Button::class.java).setOnClickListener { lancamentos() }
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
        campo(R.id.gastosHoje, TextView::class.java).text = resumoGastos()
        Notificacao.atualizar(this)

        val versao = campo(R.id.versao, TextView::class.java)
        versao.text = "Versão ${Atualizador.versaoAtual}"
        Atualizador.verificar(this, perguntar = true) { runOnUiThread { versao.text = it } }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notificacao.atualizar(this)
    }

    private fun resumoGastos(): String {
        val gastos = Gastos.todos(this)
        val porApp = Ganhos.porApp(this)
        val ganhos = porApp.values.sum()
        val gasto = Gastos.totalDoDia(gastos, LocalDate.now())
        var hoje = "Hoje: ganhos R$ ${Popup.br(ganhos)} − gastos R$ ${Popup.br(gasto)} = R$ ${Popup.br(ganhos - gasto)}"
        if (porApp.isNotEmpty()) hoje += "\n" + porApp.entries.joinToString(" · ") { "${it.key} R$ ${Popup.br(it.value)}" }
        val c = Gastos.consumo(gastos) ?: return hoje
        return hoje + "\nÚltimo consumo: ${String.format(Locale("pt", "BR"), "%.1f", c.kmPorLitro)} km/${c.unidade.lowercase()} · R$ ${Popup.br(c.custoKm)}/km"
    }

    /** Os ganhos e gastos lançados hoje; tocar num deles pergunta se quer apagar. */
    private fun lancamentos() {
        val hoje = LocalDate.now()
        fun hora(t: java.time.LocalDateTime) = t.toLocalTime().toString().take(5)
        val itens = mutableListOf<Pair<String, () -> Unit>>()
        Totais.todos(this).filter { it.hora.toLocalDate() == hoje }.forEach { t ->
            itens += "${hora(t.hora)} ${t.app}: total do dia R$ ${Popup.br(t.valor)}" to { Totais.apagar(this, t) }
        }
        Corridas.todas(this).filter { it.hora.toLocalDate() == hoje }.forEach { c ->
            itens += "${hora(c.hora)} ${c.app}: corrida R$ ${Popup.br(c.valor)}" to { Corridas.apagar(this, c) }
        }
        Gastos.todos(this).filter { it.quando.toLocalDate() == hoje }.forEach { g ->
            itens += "${hora(g.quando)} Gasto ${g.tipo.nome}: R$ ${Popup.br(g.valor)}" to { Gastos.apagar(this, g) }
        }
        itens.sortBy { it.first }
        val b = AlertDialog.Builder(this).setTitle("Lançamentos de hoje").setNegativeButton("Fechar", null)
        if (itens.isEmpty()) {
            b.setMessage("Nada lançado hoje.").show()
            return
        }
        b.setItems(itens.map { it.first }.toTypedArray()) { _, i ->
            AlertDialog.Builder(this)
                .setMessage("Apagar \"${itens[i].first}\"?")
                .setPositiveButton("Apagar") { _, _ ->
                    itens[i].second()
                    campo(R.id.gastosHoje, TextView::class.java).text = resumoGastos()
                    Notificacao.atualizar(this)
                    lancamentos()
                }
                .setNegativeButton("Cancelar") { _, _ -> lancamentos() }
                .show()
        }.show()
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
        return Config(
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

    private fun <T : android.view.View> campo(id: Int, tipo: Class<T>): T = tipo.cast(findViewById(id))!!
}
