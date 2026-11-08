# Leitor Local

Leitor pessoal de livros em PDF para Android. O aplicativo abre um PDF, extrai o
texto no próprio aparelho, mostra a página original e usa uma voz `pt-BR`
instalada no Android ou uma das vozes neurais incluídas para ler o conteúdo.
Não há conta, servidor, analytics ou API paga.

## Baixar e instalar

**Você não precisa do Android Studio para usar o aplicativo.**

[Baixar o APK da versão mais recente](https://github.com/luizpaulo-antunes/leitor-local-android/releases/latest/download/Leitor-Local-v0.14.0.apk)

No celular Android:

1. Abra o link acima e baixe o arquivo `Leitor-Local-v0.14.0.apk`.
2. Abra o APK baixado.
3. Se o Android solicitar, permita que o navegador instale aplicativos de
   fontes desconhecidas.
4. Toque em **Instalar**.

O Play Protect pode mostrar um aviso porque este é um aplicativo pessoal,
distribuído fora da Play Store. O código-fonte e todas as versões publicadas
estão disponíveis neste repositório.

## Evolução do projeto

O Leitor Local começou como um leitor de PDF com narração pelo TTS do Android e
evoluiu para uma plataforma de leitura assistida inteiramente local. A evolução
aconteceu em quatro frentes:

1. **Leitura e continuidade:** biblioteca, retomada automática, controles de
   mídia, destaque palavra por palavra e reprodução com a tela apagada.
2. **Voz neural:** Miro, Dii e Kokoro passaram a fazer parte do APK, com cache de
   áudio, tratamento específico por voz e preparação antecipada dos próximos
   trechos.
3. **Acessibilidade do documento:** OCR de páginas escaneadas, detecção de
   figuras, leitura de legendas e reconstrução da ordem de páginas em duas
   colunas.
4. **Inteligência local:** pesquisa por significado, explicação do trecho,
   resumo extrativo e reconhecimento visual, sempre com fontes do próprio PDF.

Na versão `0.14.0`, o Kokoro passou a aguardar uma reserva de pelo
menos oito trechos e aproximadamente 90 segundos antes de iniciar. A preparação
inicial pode levar um pouco mais de tempo, mas evita que a voz leia dois trechos
e pare enquanto o próximo áudio ainda está sendo sintetizado. O resultado é uma
narração muito mais contínua.

## Estado atual

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
- Permite preparar todo o livro antecipadamente, com progresso e cancelamento, para uma reprodução contínua.
- Comprime offline o cache do Kokoro em Opus e grava o áudio fora do caminho crítico da reprodução.
- Reconhece progressivamente páginas escaneadas com OCR offline e mantém o resultado em cache.
- Identifica figuras acompanhadas por legenda ou marcação de acessibilidade no PDF.
- Extrai e mostra a figura em destaque durante a narração.
- Combina legenda, descrição acessível e OCR offline para narrar o conteúdo visual sem enviar o livro para a internet.
- Respeita o foco de áudio do Android e pausa ao desconectar fones, evitando que a leitura continue no alto-falante.
- Ignora callbacks atrasados do TTS e reinicia o trecho atual de forma segura ao trocar voz ou velocidade.
- Mantém título e trecho do livro ocultos na visualização pública da tela bloqueada.
- Associa o progresso explicitamente ao livro e grava apenas quando o trecho muda, reduzindo I/O durante o karaokê.
- Valida caches pelo tamanho e pela data de modificação do PDF e limita o espaço usado por artefatos regeneráveis.
- Reconstrói offline a ordem de leitura de páginas e digitalizações com duas colunas.
- Pesquisa o livro por significado, com índice local em português e links para as páginas encontradas.
- Resume capítulos e explica o trecho atual de forma extrativa, sempre mostrando as fontes do próprio PDF.
- Classifica figuras com um modelo visual incluído no APK e combina os elementos reconhecidos com legenda, texto alternativo e OCR.

## Inteligência offline

O projeto usa diferentes técnicas de inteligência artificial e análise local.
Elas não dependem de uma IA na nuvem nem enviam o livro para um servidor:

| Recurso | Tecnologia offline | Melhoria prática |
| --- | --- | --- |
| Narração neural | Kokoro e vozes Piper executadas pelo Sherpa-ONNX | Vozes mais naturais e consistentes do que o TTS básico do sistema. |
| Cache de voz | Opus executado no próprio aplicativo | Guarda muitas horas de áudio preparado usando uma fração do espaço do PCM original. |
| Limpeza da voz | Modelo neural GTCRN aplicado ao Miro, além de ganho e limitador locais | Reduz ruído sem provocar saturação no áudio. |
| OCR | Modelo do ML Kit incluído no APK | Torna páginas escaneadas e textos dentro de figuras pesquisáveis e narráveis. |
| Análise visual | Classificador de imagens do ML Kit, combinado com legenda, texto alternativo e OCR | Produz uma descrição mais útil das figuras durante a leitura. |
| Ordem de leitura | Coordenadas dos blocos e reconstrução espacial no próprio aparelho | Evita alternar indevidamente linhas entre as duas colunas de uma página. |
| Pesquisa semântica | Normalização do português, radicais, termos relacionados, bigramas e relevância por documento | Encontra passagens relacionadas à intenção da busca, mesmo sem coincidência literal perfeita. |
| Explicação e resumo | Seleção extrativa das sentenças mais relevantes | Explica o trecho e resume capítulos sem criar uma resposta livre desconectada do livro. |

O botão de busca na tela do livro reúne as funções **Buscar**, **Explicar** e
**Resumir**. Cada resultado mostra a página e o fragmento que o originou; ao
tocar em uma fonte, o leitor vai diretamente até ela. Isso torna as respostas
verificáveis e reduz o risco de conteúdo inventado.

Não há um LLM generativo embutido. A parte textual usa um índice semântico leve
e seleção extrativa, uma escolha intencional para funcionar em aparelhos
Android sem exigir centenas de megabytes adicionais de modelo ou grande consumo
de memória. Já a voz, o OCR, a redução de ruído e a classificação de imagens
usam modelos de aprendizado de máquina incluídos no aplicativo.

Toda essa análise acontece no aparelho. O manifesto não solicita permissão de
internet e não existe fallback para serviços externos; PDFs, buscas, áudios,
imagens e índices permanecem locais.

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

O Kokoro usa o modelo oficial FP32, recebe fonemas pt-BR produzidos localmente e
preserva a saída em 24 kHz. Antes de iniciar a reprodução, o aplicativo mantém
uma reserva de pelo menos oito trechos e cerca de 90 segundos de áudio para
evitar interrupções entre parágrafos. Somente o Miro passa pelo redutor de ruído
de 16 kHz, pois os demais modelos já geram áudio limpo.

Nos ajustes de leitura, o botão **Preparar livro** sintetiza e guarda todos os
trechos com a voz e a velocidade selecionadas. O progresso aparece no aplicativo
e em uma notificação, pode ser cancelado e continua com a tela apagada. Quando o
livro está pronto, a reprodução usa o áudio já armazenado e não precisa esperar
o Kokoro entre os parágrafos. Durante a narração, o OCR é temporariamente
pausado para deixar o processador disponível para a voz; em livros escaneados,
o preparo completo é liberado depois que o OCR termina de montar os trechos.

Ao abrir um livro com uma voz neural selecionada, o aplicativo começa a preparar em
segundo plano até três páginas a partir da posição atual. Cada trecho é
sintetizado, recebe o tratamento específico da voz e fica em um cache local. Se
você tocar em reproduzir antes do fim, a leitura recebe prioridade e o cache
continua sendo abastecido adiante durante a reprodução. O cache de áudio é
codificado em Opus nas taxas compatíveis — incluindo os 24 kHz do Kokoro — e em
PCM16 nas demais vozes. O codificador Opus faz parte do APK, funciona offline
também no Android 8 e 9 e não depende do codec do fabricante. O cache é limitado
a 512 MB e o conteúdo do livro não é enviado para a internet.

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

Textos impressos em alfabeto latino produzem os melhores resultados.
Manuscritos, diagramas complexos e layouts com três ou mais colunas ainda podem
exigir correção na ordem de leitura.

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
