package app.corridaverde

import android.os.Build

/** O que depende da versão do Android deste celular. */
object Celular {
    /** O aviso da 99 tira um print da oferta, e a Acessibilidade só tira print a partir do Android 11. */
    val semAviso99 get() =
        "O aviso da 99 precisa do Android 11 ou mais novo. Este celular tem Android ${Build.VERSION.RELEASE}: o aviso da Uber funciona normal."
}
