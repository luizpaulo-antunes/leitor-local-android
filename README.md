# Leitor Local

Leitor pessoal de livros em PDF para Android. O aplicativo abre um PDF, extrai o
texto no próprio aparelho, mostra a página original e usa uma voz `pt-BR`
instalada no Android para ler o conteúdo. Não há conta, servidor, analytics ou
API paga.

## Estado do MVP

- Abre PDFs pelo seletor seguro do Android.
- Extrai texto de PDFs digitais com PDFBox.
- Renderiza a página atual com `PdfRenderer`.
- Limpa quebras de linha e divide o texto em trechos narráveis.
- Lê com o Android Text-to-Speech, inclusive com a tela apagada.
- Permite pausar, continuar, avançar e voltar trechos.
- Lista vozes em português instaladas e controla a velocidade.
- Salva automaticamente o último livro e a posição.

PDFs formados apenas por imagens ainda precisam de OCR. O suporte a ML Kit e a
voz neural Piper offline são as próximas extensões planejadas.

## Como executar

1. Instale o Android Studio com JDK 17 e Android SDK 36 ou mais recente.
2. Abra esta pasta no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Execute em um celular Android 8.0 (API 26) ou superior.

Na primeira leitura, o Android pode solicitar permissão para mostrar a
notificação usada pela reprodução em segundo plano.
