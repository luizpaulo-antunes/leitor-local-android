# Leitor Local

Leitor pessoal de livros em PDF para Android. O aplicativo abre um PDF, extrai o
texto no próprio aparelho, mostra a página original e usa uma voz `pt-BR`
instalada no Android ou uma das vozes neurais incluídas para ler o conteúdo.
Não há conta, servidor, analytics ou API paga.

## Baixar e instalar

**Você não precisa do Android Studio para usar o aplicativo.**

[Baixar o APK da versão mais recente](https://github.com/luizpaulo-antunes/leitor-local-android/releases/latest/download/Leitor-Local-v0.12.0.apk)

No celular Android:

1. Abra o link acima e baixe o arquivo `Leitor-Local-v0.12.0.apk`.
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
- Exibe a biblioteca em uma grade visual de capas, com progresso e quantidade de páginas.
- Ignora automaticamente números de página, cabeçalhos e rodapés repetidos.
- Prepara parágrafos, títulos e pausas para uma narração mais natural.
- Identifica e prioriza vozes em português de alta qualidade instaladas no aparelho.
- Mantém um cache local do texto processado para reabrir livros sem reler todas as páginas.
- Exibe o trecho atual em modo de foco, sobre a página suavemente desfocada.
- Usa um card de leitura sólido e autoajustável sobre a página desfocada, com contraste consistente em qualquer PDF.
- Mostra avisos de páginas ignoradas como notificações temporárias, sem reduzir a área de leitura.
- Destaca e acompanha automaticamente cada palavra durante a narração.
- Mantém a página como foco principal e reúne voz e velocidade em ajustes recolhíveis.
- Mantém a tela ligada enquanto o leitor está visível e narrando, liberando-a ao pausar ou sair.
- Mostra anterior, pausar/continuar e próximo na notificação, tela bloqueada e painel de mídia.
- Ao abrir pela notificação, retorna diretamente ao livro ativo sem interromper nem recriar a voz neural.
- Separa visualmente títulos do início dos parágrafos e preserva o destaque palavra por palavra.
- Reúne progresso, navegação, reprodução, voz e velocidade em um player inferior compacto.
- Inclui Miro, Dii e três vozes Kokoro pt-BR, todos executados offline pelo Sherpa-ONNX.
- Aplica um perfil próprio a cada família: Miro recebe redução de ruído; Dii e Kokoro preservam a resolução original.
- Reforça o volume neural com limitador contra saturação; o Miro recebe 43% de ganho adicional para equilibrar a Dii.
- Prepara e guarda localmente as próximas páginas em áudio tratado para iniciar e continuar a leitura com menos espera.
- Reconhece progressivamente páginas escaneadas com OCR offline e mantém o resultado em cache.
- Identifica figuras acompanhadas por legenda ou marcação de acessibilidade no PDF.
- Extrai e mostra a figura em destaque durante a narração.
- Combina legenda, descrição acessível e OCR offline para narrar o conteúdo visual sem enviar o livro para a internet.
- Respeita o foco de áudio do Android e pausa ao desconectar fones, evitando que a leitura continue no alto-falante.
- Ignora callbacks atrasados do TTS e reinicia o trecho atual de forma segura ao trocar voz ou velocidade.
- Mantém título e trecho do livro ocultos na visualização pública da tela bloqueada.
- Associa o progresso explicitamente ao livro e grava apenas quando o trecho muda, reduzindo I/O durante o karaokê.
- Valida caches pelo tamanho e pela data de modificação do PDF e limita o espaço usado por artefatos regeneráveis.

## Figuras durante a leitura

Ao encontrar uma figura, o aplicativo a intercala na ordem da leitura. Durante
a narração, a imagem aparece sem desfoque, acompanhada da legenda e da descrição
que está sendo falada. O reconhecimento de texto da figura usa o ML Kit
embutido no APK e funciona offline.

Na primeira abertura de um livro, a análise das figuras pode levar alguns
instantes. O texto processado, as descrições e as imagens extraídas ficam em
cache no próprio aparelho; as próximas aberturas não repetem esse trabalho.
PDFs bem estruturados, com legendas e texto alternativo, produzem resultados
melhores. Diagramas complexos sem texto ou descrição acessível ainda recebem
uma descrição limitada ao conteúdo que o aplicativo consegue detectar.

## Vozes neurais offline incluídas

Na tela do livro, abra o seletor **Voz** e escolha entre Miro, Dii e as vozes
**Kokoro Dora, Alex e Santa**. Todos os motores e modelos fazem parte do APK; não é necessário
instalar outro aplicativo, baixar arquivos adicionais ou alterar as
configurações do Android. Miro e Dii continuam disponíveis para comparação e
como alternativas mais leves de processamento.

O Kokoro usa a variante oficial INT8 para carregar mais rápido, recebe fonemas
pt-BR produzidos localmente e preserva a saída em 24 kHz. A reprodução começa
assim que o primeiro trecho fica pronto, e os trechos menores reduzem a espera
inicial. Somente o Miro passa pelo redutor de ruído de 16 kHz, pois os demais
modelos já geram áudio limpo.

Ao abrir um livro com uma voz neural selecionada, o aplicativo começa a preparar em
segundo plano até três páginas a partir da posição atual. Cada trecho é
sintetizado, recebe o tratamento específico da voz e fica em um cache local. Se
você tocar em reproduzir antes do fim, a leitura recebe prioridade e o cache
continua sendo abastecido adiante durante a reprodução. O cache de áudio é
limitado a 512 MB e o conteúdo do livro não é enviado para a internet.

O APK é grande porque inclui todos os modelos, o OCR offline e suporte a
aparelhos ARM64 e ARM32. Os modelos Miro e Dii são destinados a uso pessoal e
não comercial e são redistribuídos sem alterações. Consulte
`THIRD_PARTY_NOTICES.md` para as licenças de todos os motores e modelos.

O processamento também ignora cabeçalhos, rodapés e números marcados pelo PDF
como artefatos de paginação. Para documentos sem essa marcação, há um filtro de
margens que reconhece títulos com letras muito espaçadas e números anexados.

O mesmo OCR também é aplicado às páginas que não possuem texto selecionável.
O aplicativo prioriza a página atual e as três seguintes e continua preparando
o restante do livro em segundo plano. O resultado de cada página, inclusive de
páginas realmente vazias, fica em cache e não precisa ser processado novamente.
Livros totalmente escaneados recebem um processamento inicial curto para que a
leitura possa começar antes do término do livro inteiro.

Textos impressos em alfabeto latino produzem os melhores resultados. Manuscritos,
diagramas complexos e páginas com várias colunas ainda podem exigir correção na
ordem de leitura.

## Para desenvolvedores

Esta seção é necessária somente para quem quiser modificar ou compilar o
código. Para instalar o aplicativo, use o APK disponibilizado acima.

O projeto utiliza JDK 17 e Android SDK 37. Ele pode ser compilado pelo Android
Studio ou diretamente pelo Gradle, em um ambiente que tenha o Android SDK
configurado:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

O mesmo conjunto de verificações é executado automaticamente pelo GitHub Actions
a cada alteração enviada à branch principal ou a um pull request. O workflow
serve para validar o código; o APK instalável e assinado de forma compatível com
as versões anteriores é publicado somente na página de Releases.

Para manter o histórico Git leve, os modelos Dii e Kokoro não
ficam versionados no repositório. Em um clone novo, o primeiro build baixa os
pacotes oficiais uma única vez, confere o SHA-256 e inclui somente os arquivos
necessários no APK. Isso só afeta quem recompila o projeto; o APK publicado já
contém todas as vozes e não precisa de internet para usá-las.

O aplicativo é compatível com Android 8.0 (API 26) ou superior.

Na primeira leitura, o Android pode solicitar permissão para mostrar a
notificação usada pela reprodução em segundo plano.
