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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PARAR) {
            fundo.post { encerrar() }
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
                atual?.let { Videos.concluir(this, it, ok = true) }
                atual = proximo
                proximo = null
                runCatching { Videos.limpar(this) }
            }
            // Não conseguiu abrir o próximo pedaço: o gravador já parou e o arquivo está fechado.
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> {
                atual?.let { Videos.concluir(this, it, ok = true) }
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
        atual?.let { Videos.concluir(this, it, ok = comecou && parou) }
        atual = null
        proximo?.let { Videos.concluir(this, it, ok = false) }
        proximo = null
        comecou = false
        runCatching { Videos.limpar(this) }
        if (situacao.endsWith("gravando")) situacao = "${agora()} gravação encerrada"
        tela.post {
            gravando = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Notificacao.atualizar(this)
        }
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
        private const val ID = 2
        private const val CANAL = "gravacao"
        private const val TAMANHO_PEDACO = 25L * 1024 * 1024

        @Volatile var gravando = false
            private set
        @Volatile var situacao = "nenhuma gravação ainda"
            private set

        /** Começa pela [GravarActivity]: câmera e microfone só podem ser ligados com uma tela do app aberta. */
        fun iniciar(ctx: Context) {
            ctx.startActivity(Intent(ctx, GravarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun parar(ctx: Context) {
            if (gravando) ctx.startService(Intent(ctx, GravacaoService::class.java).setAction(PARAR))
        }

        private fun agora() = LocalTime.now().withNano(0).toString()
    }
}
