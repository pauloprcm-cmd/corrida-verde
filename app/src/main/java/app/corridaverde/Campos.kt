package app.corridaverde

import android.text.InputType
import android.text.method.DigitsKeyListener
import android.widget.EditText

/**
 * Campo de valor que aceita vírgula ("30,50"). O teclado de número com decimais do Android, sozinho,
 * só deixa entrar o ponto em muitos celulares (Samsung, por exemplo): a vírgula some ao ser digitada.
 * Aqui o teclado continua o de números, mas o campo aceita os dois.
 */
fun EditText.aceitarVirgula() {
    keyListener = DigitsKeyListener.getInstance("0123456789,.")
    setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
}

/**
 * Teclado de número com decimais? Confere a classe antes: o "maiúscula em cada palavra" dos campos de
 * texto usa o mesmo bit do "decimal" e, sem isso, o campo do nome abria só com o teclado de números.
 */
fun ehDecimal(tipo: Int) =
    (tipo and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER && (tipo and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0
