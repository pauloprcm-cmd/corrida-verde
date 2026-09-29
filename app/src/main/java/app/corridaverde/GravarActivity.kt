package app.corridaverde

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/**
 * Tela invisível que só liga a gravação e fecha. O Android só deixa um serviço usar a
 * câmera e o microfone em segundo plano se ele for iniciado com uma tela do app aberta.
 */
class GravarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val faltam = PERMISSOES.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (faltam.isEmpty()) comecar() else requestPermissions(faltam.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            comecar()
        } else {
            Toast.makeText(this, "Sem a câmera e o microfone não dá para gravar", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun comecar() {
        startForegroundService(Intent(this, GravacaoService::class.java))
        finish()
    }

    companion object {
        val PERMISSOES = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        fun permitido(ctx: Context) = PERMISSOES.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}
