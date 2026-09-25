package app.corridaverde

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import java.io.File

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
        campo(R.id.posicaoY, EditText::class.java).setText(cfg.posicaoY.toString())
        campo(R.id.diagnostico, CheckBox::class.java).isChecked = cfg.diagnostico

        campo(R.id.ativar, Button::class.java).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        campo(R.id.salvar, Button::class.java).setOnClickListener {
            lerCampos()?.let {
                it.salvar(this)
                Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
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
        campo(R.id.compartilhar, Button::class.java).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, diagnostico())
            }, "Enviar diagnóstico"))
        }
    }

    override fun onResume() {
        super.onResume()
        val ativo = LeitorService.instancia != null
        campo(R.id.status, TextView::class.java).text =
            if (ativo) "✅ Leitura ativa" else "❌ Leitura desligada: toque em \"Ativar leitura\""
        campo(R.id.textoDiagnostico, TextView::class.java).text = diagnostico()

        val versao = campo(R.id.versao, TextView::class.java)
        versao.text = "Versão ${Atualizador.versaoAtual}"
        Atualizador.verificar(this, perguntar = true) { runOnUiThread { versao.text = it } }
    }

    private fun diagnostico(): String {
        val f = File(filesDir, LeitorService.ARQUIVO_DIAGNOSTICO)
        return if (f.exists()) f.readText() else "Nenhuma leitura ainda. Ligue o modo diagnóstico e abra uma oferta na Uber."
    }

    private fun lerCampos(): Config? {
        fun num(id: Int) = campo(id, EditText::class.java).text.toString().trim().replace(',', '.').toDoubleOrNull()
        val verde = num(R.id.limiteVerde)?.toInt()
        val amarelo = num(R.id.limiteAmarelo)?.toInt()
        val busca = num(R.id.buscaMax)
        val y = num(R.id.posicaoY)?.toInt()
        if (verde == null || amarelo == null || busca == null || y == null || amarelo > verde) {
            Toast.makeText(this, "Confira os números (o amarelo não pode passar do verde)", Toast.LENGTH_LONG).show()
            return null
        }
        return Config(
            luxo = campo(R.id.luxo, RadioButton::class.java).isChecked,
            limiteVerde = verde,
            limiteAmarelo = amarelo,
            buscaMaxKm = busca,
            posicaoY = y,
            diagnostico = campo(R.id.diagnostico, CheckBox::class.java).isChecked,
        )
    }

    private fun <T : android.view.View> campo(id: Int, tipo: Class<T>): T = tipo.cast(findViewById(id))!!
}
