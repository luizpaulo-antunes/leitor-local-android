# Leitor Local

Leitor pessoal de livros em PDF para Android. O aplicativo abre um PDF, extrai o
texto no próprio aparelho, mostra a página original e usa uma voz `pt-BR`
instalada no Android para ler o conteúdo. Não há conta, servidor, analytics ou
API paga.

## Baixar e instalar

**Você não precisa do Android Studio para usar o aplicativo.**

[Baixar o APK da versão mais recente](https://github.com/luizpaulo-antunes/leitor-local-android/releases/latest/download/Leitor-Local-v0.4.1.apk)

No celular Android:

1. Abra o link acima e baixe o arquivo `Leitor-Local-v0.4.1.apk`.
2. Abra o APK baixado.
3. Se o Android solicitar, permita que o navegador instale aplicativos de
   fontes desconhecidas.
4. Toque em **Instalar**.

O Play Protect pode mostrar um aviso porque este é um aplicativo pessoal,
distribuído fora da Play Store. O código-fonte e todas as versões publicadas
estão disponíveis neste repositório.

## Estado do MVP

- Abre PDFs pelo seletor seguro do Android.
- Extrai texto de PDFs digitais com PDFBox.
- Renderiza a página atual com `PdfRenderer`.
- Limpa quebras de linha e divide o texto em trechos narráveis.
- Lê com o Android Text-to-Speech, inclusive com a tela apagada.
- Permite pausar, continuar, avançar e voltar trechos.
- Lista vozes em português instaladas e controla a velocidade.
- Salva automaticamente o último livro e a posição.
- Mostra uma biblioteca com todos os PDFs abertos e o percentual lido.
- Ignora automaticamente números de página, cabeçalhos e rodapés repetidos.
- Prepara parágrafos, títulos e pausas para uma narração mais natural.
- Identifica e prioriza vozes em português de alta qualidade instaladas no aparelho.
- Mantém um cache local do texto processado para reabrir livros sem reler todas as páginas.
- Exibe o trecho atual em modo de foco, sobre a página suavemente desfocada.
- Inclui a voz neural brasileira Miro, executada offline pelo Sherpa-ONNX.

## Voz neural offline incluída

Na tela do livro, abra o seletor **Voz** e escolha
**Miro — narrador neural natural · offline**. O motor e o modelo já fazem parte do APK; não
é necessário instalar outro aplicativo, baixar arquivos adicionais ou alterar
as configurações do Android.

A primeira reprodução com Miro pode demorar alguns segundos enquanto o motor é
preparado. Depois disso, a síntese funciona no próprio aparelho e não envia o
texto do livro para a internet.

O APK com a voz neural tem aproximadamente 129 MB e suporta aparelhos ARM64 e
ARM32. O modelo Miro possui licença CC BY-NC-SA 4.0 e, portanto, esta distribuição
é destinada a uso pessoal e não comercial. Consulte `THIRD_PARTY_NOTICES.md`.

PDFs formados apenas por imagens ainda precisam de OCR. O suporte a ML Kit e a
voz neural Piper offline são as próximas extensões planejadas.

## Para desenvolvedores

Esta seção é necessária somente para quem quiser modificar ou compilar o
código. Para instalar o aplicativo, use o APK disponibilizado acima.

O projeto utiliza JDK 17 e Android SDK 36. Ele pode ser compilado pelo Android
Studio ou diretamente pelo Gradle, em um ambiente que tenha o Android SDK
configurado:

```bash
./gradlew assembleDebug
```

O aplicativo é compatível com Android 8.0 (API 26) ou superior.

Na primeira leitura, o Android pode solicitar permissão para mostrar a
notificação usada pela reprodução em segundo plano.
