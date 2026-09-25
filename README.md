# Corrida Verde

App Android para taxista de São Paulo. Lê a oferta de corrida no app de motorista da Uber e mostra um popup
com quanto ela paga em relação ao taxímetro da Prefeitura de SP:

- **Verde**: 100% do taxímetro ou mais
- **Amarelo**: de 85% a 100%
- **Vermelho**: abaixo de 85%
- Busca acima de 3 km rebaixa um nível.

O taxímetro usa a tabela vigente desde 11/08/2025 (comum: R$ 6,55 + R$ 4,80/km; luxo: R$ 9,83 + R$ 7,20/km),
com bandeira 2 (+30% no km) das 20h às 6h de segunda a sábado e o dia todo em domingos e feriados.
Todos os limites podem ser ajustados no app.

O app **só lê** a tela: não toca, não aceita e não recusa corridas.

## Instalar

Baixe o APK mais recente:
https://github.com/pauloprcm-cmd/corrida-verde/releases/latest/download/corrida-verde.apk

Depois abra o app e toque em **Ativar leitura**. Se o Android bloquear a Acessibilidade, vá em
Configurações › Apps › Corrida Verde › ⋮ › Permitir configurações restritas.

## Atualização

Ao abrir, o app busca a release mais nova no GitHub e instala por cima (o Android pede confirmação e,
na primeira vez, permissão para "instalar apps desconhecidos" do Corrida Verde). No Android 12 ou mais novo,
o serviço também verifica a cada 6 horas e atualiza em silêncio quando o Android permite; se o Android
exigir confirmação, ele espera o app ser aberto, sem abrir nada por cima da Uber.
