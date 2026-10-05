package app.corridaverde

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Range
import java.time.LocalTime

/**
 * Grava a câmera da frente e o microfone durante a corrida, em 480p para não pesar.
 * Os vídeos são gravados em pedaços de uns 5 minutos: se o Android fechar o app no meio,
 * só o último pedaço se perde. Se outro app pedir a câmera (a selfie da Uber, por exemplo),
 * a gravação para e o que já foi gravado fica salvo.
 *
 * Para quem esquece gravando: avisa com som depois de 1 hora, quando chega oferta da Uber ou da 99
 * e quando o motorista registra o valor de uma corrida. Nunca para sozinha: num assalto o motorista
 * pode não conseguir responder, e é aí que a gravação mais importa.
 */
class GravacaoService : Service() {
    private val linha = HandlerThread("gravacao").apply { start() }
    private val fundo = Handler(linha.looper)
    private val tela = Handler(Looper.getMainLooper())

    // Só usados na linha de gravação.
    private var camera: CameraDevice? = null
    private var gravador: MediaRecorder? = null
    private var atual: Videos.Destino? = null
    private var proximo: Videos.Destino? = null
    private var comecou = false
    /** Pedaços desta gravação que deram certo, para o botão Guardar este vídeo. */
    private val pedacos = mutableListOf<String>()

    /** Só na tela. O lembrete de tempo: 1 hora depois de começar, e de novo a cada 30 min (15 se ninguém respondeu). */
    private val lembrete = Runnable { avisar("Gravando há ${duracao()}. A corrida terminou?") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PARAR) {
            fundo.post { encerrar() }
            return START_NOT_STICKY
        }
        if (intent?.action == CONTINUAR) {
            getSystemService(NotificationManager::class.java).cancel(ID_LEMBRETE)
            silencioAte = System.currentTimeMillis() + 30 * 60_000L
            tela.removeCallbacks(lembrete)
            tela.postDelayed(lembrete, 30 * 60_000L)
            return START_NOT_STICKY
        }
        if (intent?.action == SUSPEITA) {
            if (gravando) intent.getStringExtra(MOTIVO)?.let { avisar("$it e a gravação continua ligada (há ${duracao()}). A corrida terminou?") }
            return START_NOT_STICKY
        }
        if (gravando) return START_NOT_STICKY
        val ligou = runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(ID, notificacao(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(ID, notificacao())
            }
        }
        if (ligou.isFailure) {
            situacao = "${agora()} o Android não deixou gravar: ${ligou.exceptionOrNull()?.message}"
            stopSelf()
            return START_NOT_STICKY
        }
        gravando = true
        desde = System.currentTimeMillis()
        silencioAte = 0
        tela.postDelayed(lembrete, 60 * 60_000L)
        situacao = "${agora()} abrindo a câmera"
        Notificacao.atualizar(this)
        fundo.post { runCatching { abrir() }.onFailure { falhou(it) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fundo.post { encerrar() }
        linha.quitSafely()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun abrir() {
        val cm = getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: cm.cameraIdList.first()
        val c = cm.getCameraCharacteristics(id)
        val mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        val (largura, altura) = Videos.escolherTamanho(mapa.getOutputSizes(MediaRecorder::class.java).map { it.width to it.height })
        // 15 quadros por segundo bastam para registro e gastam metade do processamento.
        val faixa = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 15 }?.minWithOrNull(compareBy<Range<Int>>({ it.upper }, { -it.lower }))

        val destino = Videos.novo(this)
        atual = destino
        val g = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        gravador = g
        g.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        g.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        g.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        g.setOutputFile(destino.fd.fileDescriptor)
        g.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        g.setVideoSize(largura, altura)
        g.setVideoFrameRate(faixa?.upper ?: 30)
        g.setVideoEncodingBitRate(600_000)
        g.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        g.setAudioEncodingBitRate(64_000)
        g.setAudioSamplingRate(44_100)
        g.setOrientationHint(c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270)
        g.setMaxFileSize(TAMANHO_PEDACO)
        g.setOnInfoListener { _, oQue, _ -> runCatching { trocarPedaco(oQue) } }
        g.setOnErrorListener { _, oQue, _ -> falhou(IllegalStateException("gravador: erro $oQue")) }
        g.prepare()

        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(cam: CameraDevice) {
                camera = cam
                runCatching { iniciarSessao(cam, faixa) }.onFailure { falhou(it) }
            }
            override fun onDisconnected(cam: CameraDevice) {
                situacao = "${agora()} outro app pegou a câmera; o que foi gravado ficou salvo"
                encerrar()
            }
            override fun onError(cam: CameraDevice, erro: Int) = falhou(IllegalStateException("câmera: erro $erro"))
        }, fundo)
    }

    @Suppress("DEPRECATION")
    private fun iniciarSessao(cam: CameraDevice, faixa: Range<Int>?) {
        val superficie = gravador!!.surface
        cam.createCaptureSession(listOf(superficie), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                runCatching {
                    val pedido = cam.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(superficie)
                        faixa?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                    }
                    s.setRepeatingRequest(pedido.build(), null, fundo)
                    gravador!!.start()
                    comecou = true
                    situacao = "${agora()} gravando"
                }.onFailure { falhou(it) }
            }
            override fun onConfigureFailed(s: CameraCaptureSession) = falhou(IllegalStateException("a câmera recusou a configuração"))
        }, fundo)
    }

    /** Perto do tamanho do pedaço, o gravador passa para um arquivo novo sem parar de gravar. */
    private fun trocarPedaco(oQue: Int) {
        when (oQue) {
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> {
                val d = Videos.novo(this)
                proximo = d
                runCatching { gravador!!.setNextOutputFile(d.fd.fileDescriptor) }.onFailure {
                    Videos.concluir(this, d, ok = false)
                    proximo = null
                }
            }
            MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> {
                atual?.let { Videos.concluir(this, it, ok = true); pedacos += it.chave }
                atual = proximo
                proximo = null
                runCatching { Videos.limpar(this) }
            }
            // Não conseguiu abrir o próximo pedaço: o gravador já parou e o arquivo está fechado.
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                atual?.let { Videos.concluir(this, it, ok = true); pedacos += it.chave }
                atual = null
                encerrar()
            }
        }
    }

    private fun falhou(t: Throwable) {
        situacao = "${agora()} erro: ${t.message}"
        encerrar()
    }

    private fun encerrar() {
        val parou = runCatching { gravador!!.stop() }.isSuccess
        runCatching { gravador?.release() }
        gravador = null
        runCatching { camera?.close() }
        camera = null
        atual?.let {
            val ok = comecou && parou
            Videos.concluir(this, it, ok)
            if (ok) pedacos += it.chave
        }
        atual = null
        proximo?.let { Videos.concluir(this, it, ok = false) }
        proximo = null
        comecou = false
        runCatching { Videos.limpar(this) }
        if (situacao.endsWith("gravando")) situacao = "${agora()} gravação encerrada"
        val gravados = pedacos.toList()
        pedacos.clear()
        tela.post {
            tela.removeCallbacks(lembrete)
            getSystemService(NotificationManager::class.java).cancel(ID_LEMBRETE)
            if (gravando && gravados.isNotEmpty()) oferecerGuardar(gravados)
            gravando = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Notificacao.atualizar(this)
        }
    }

    /** Só na tela. Aviso com som e vibração, com Parar e Continuar. Sem resposta, o lembrete de tempo volta em 15 min. */
    private fun avisar(texto: String) {
        val agora = System.currentTimeMillis()
        if (!gravando || agora < silencioAte) return
        // Uma oferta atrás da outra não vira uma fila de avisos.
        silencioAte = agora + 5 * 60_000L
        tela.removeCallbacks(lembrete)
        tela.postDelayed(lembrete, 15 * 60_000L)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL_LEMBRETE, "Lembrete de gravação ligada", NotificationManager.IMPORTANCE_HIGH))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val parar = PendingIntent.getService(this, 0, Intent(this, GravacaoService::class.java).setAction(PARAR), flags)
        val continuar = PendingIntent.getService(this, 1, Intent(this, GravacaoService::class.java).setAction(CONTINUAR), flags)
        nm.notify(ID_LEMBRETE, Notification.Builder(this, CANAL_LEMBRETE)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("A gravação continua ligada")
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(Notification.Action.Builder(null, "Parar gravação", parar).build())
            .addAction(Notification.Action.Builder(null, "Continuar gravando", continuar).build())
            .build())
    }

    /** Acabou de parar: oferece guardar o vídeo, para ele nunca entrar na limpeza dos 5 GB. */
    private fun oferecerGuardar(gravados: List<String>) {
        val nm = getSystemService(NotificationManager::class.java)
        val guardar = PendingIntent.getBroadcast(
            this, 4, Intent(this, GuardarVideoReceiver::class.java).putExtra(GuardarVideoReceiver.PEDACOS, gravados.toTypedArray()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(GuardarVideoReceiver.ID, Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Gravação salva (${duracao()})")
            .setContentText("Aconteceu algo nesta corrida? Guarde o vídeo para ele nunca ser apagado.")
            .setStyle(Notification.BigTextStyle().bigText(
                "Aconteceu algo nesta corrida? Guarde o vídeo: os vídeos guardados nunca são apagados na limpeza dos 5 GB."))
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Guardar este vídeo", guardar).build())
            .build())
    }

    private fun duracao(): String {
        val min = ((System.currentTimeMillis() - desde) / 60_000).toInt()
        return if (min < 60) "$min min" else "${min / 60} h" + (if (min % 60 > 0) " ${min % 60}" else "")
    }

    private fun notificacao(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Gravação da corrida", NotificationManager.IMPORTANCE_LOW))
        val parar = PendingIntent.getService(
            this, 0, Intent(this, GravacaoService::class.java).setAction(PARAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Gravando a corrida")
            .setContentText("Câmera da frente e microfone · salva em Movies/${Videos.PASTA}")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Parar gravação", parar).build())
            .build()
    }

    companion object {
        const val PARAR = "app.corridaverde.PARAR_GRAVACAO"
        private const val CONTINUAR = "app.corridaverde.CONTINUAR_GRAVACAO"
        private const val SUSPEITA = "app.corridaverde.SUSPEITA_GRAVACAO"
        private const val MOTIVO = "motivo"
        private const val ID = 2
        private const val ID_LEMBRETE = 5
        private const val CANAL = "gravacao"
        private const val CANAL_LEMBRETE = "lembrete_gravacao"
        private const val TAMANHO_PEDACO = 25L * 1024 * 1024

        @Volatile var gravando = false
            private set
        @Volatile var situacao = "nenhuma gravação ainda"
            private set
        @Volatile private var desde = 0L
        /** Até quando não avisa de novo (depois de Continuar, ou logo depois de um aviso). */
        @Volatile private var silencioAte = 0L

        /** Começa pela [GravarActivity]: câmera e microfone só podem ser ligados com uma tela do app aberta. */
        fun iniciar(ctx: Context) {
            ctx.startActivity(Intent(ctx, GravarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /**
         * Algo indica que a corrida de rua acabou ([motivo] completa a frase do aviso, ex.: "Chegou uma oferta da Uber").
         * Sem gravação ligada, não faz nada.
         */
        fun avisarSeGravando(ctx: Context, motivo: String) {
            if (!gravando || System.currentTimeMillis() < silencioAte) return
            runCatching { ctx.startService(Intent(ctx, GravacaoService::class.java).setAction(SUSPEITA).putExtra(MOTIVO, motivo)) }
        }

        fun parar(ctx: Context) {
            if (gravando) ctx.startService(Intent(ctx, GravacaoService::class.java).setAction(PARAR))
        }

        private fun agora() = LocalTime.now().withNano(0).toString()
    }
}
