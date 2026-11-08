# Avisos de terceiros

## Sherpa-ONNX

O aplicativo inclui o runtime Sherpa-ONNX 2.0.1, desenvolvido pelo projeto
Next-gen Kaldi e distribuído sob a licença Apache 2.0.

- Projeto: https://github.com/k2-fsa/sherpa-onnx
- Licença: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE

## Piper Phonemize

O aplicativo inclui o Piper Phonemize 1.5.9 para converter o texto em português
nos fonemas consumidos pelo modelo neural. A biblioteca é distribuída sob a
licença GNU General Public License v3.0.

- Projeto: https://github.com/csukuangfj/piper-phonemize
- Licença: https://www.gnu.org/licenses/gpl-3.0.html

## Vozes neurais Miro e Dii pt-BR

O aplicativo inclui os modelos `vits-piper-pt_BR-miro-high` e
`vits-piper-pt_BR-dii-high`, derivados do trabalho do OpenVoiceOS. Miro é uma
voz masculina e Dii é uma voz feminina; ambas pertencem à TigreGotico Lda.
As páginas oficiais atuais declaram a licença Creative Commons
Attribution-NonCommercial-NoDerivatives 4.0 International (CC BY-NC-ND 4.0).

- Miro: https://huggingface.co/OpenVoiceOS/pipertts_pt-BR_miro
- Dii: https://huggingface.co/OpenVoiceOS/pipertts_pt-BR_dii
- Licença: https://creativecommons.org/licenses/by-nc-nd/4.0/

Os modelos podem ser usados e compartilhados, intactos e com atribuição, para
fins pessoais e não comerciais. Não podem ser modificados, adaptados nem usados
para criar vozes derivadas. Os avisos recebidos com os pacotes convertidos pelo
Sherpa-ONNX também são preservados nos assets do aplicativo; quando esses avisos
divergem da página oficial atual, este projeto adota os termos atuais mais
restritivos.

## Kokoro 82M multilíngue

O aplicativo inclui o Kokoro v1.0 multilíngue FP32 e sua tabela oficial de
estilos de voz, executados localmente pelo Sherpa-ONNX. O pacote é distribuído sob a
licença Apache 2.0, preservada junto aos assets do modelo.

- Modelo: https://huggingface.co/hexgrad/Kokoro-82M
- Pacote Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- Licença: https://www.apache.org/licenses/LICENSE-2.0

## GTCRN Speech Enhancement

O aplicativo inclui o modelo `gtcrn_simple.onnx` para redução offline de ruído
na voz sintetizada. O modelo deriva da implementação GTCRN de Rong Xiaobin,
distribuída sob a licença MIT, e foi empacotado para o Sherpa-ONNX pelo projeto
k2-fsa.

- Projeto original: https://github.com/Xiaobin-Rong/gtcrn
- Modelo para Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speech-enhancement-models
- Licença: https://github.com/Xiaobin-Rong/gtcrn/blob/main/LICENSE

## Google ML Kit Text Recognition e Image Labeling

O aplicativo inclui os modelos do Google ML Kit para reconhecer offline o texto
e os elementos visuais presentes nas figuras extraídas dos PDFs.

- Documentação: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Image Labeling: https://developers.google.com/ml-kit/vision/image-labeling/android
- Termos: https://developers.google.com/terms
