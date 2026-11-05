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

## Voz neural Miro pt-BR

O aplicativo inclui o modelo `vits-piper-pt_BR-miro-high`, derivado do trabalho
do OpenVoiceOS. O modelo é distribuído sob a licença Creative Commons
Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0).

- Modelo: https://huggingface.co/OpenVoiceOS/pipertts_pt-BR_miro
- Licença: https://creativecommons.org/licenses/by-nc-sa/4.0/

O modelo pode ser usado livremente para fins pessoais e não comerciais. Seu uso
comercial não é permitido pela licença atual. O aviso original do modelo também
é preservado nos assets do aplicativo.

## GTCRN Speech Enhancement

O aplicativo inclui o modelo `gtcrn_simple.onnx` para redução offline de ruído
na voz sintetizada. O modelo deriva da implementação GTCRN de Rong Xiaobin,
distribuída sob a licença MIT, e foi empacotado para o Sherpa-ONNX pelo projeto
k2-fsa.

- Projeto original: https://github.com/Xiaobin-Rong/gtcrn
- Modelo para Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speech-enhancement-models
- Licença: https://github.com/Xiaobin-Rong/gtcrn/blob/main/LICENSE

## Google ML Kit Text Recognition

O aplicativo inclui o reconhecedor de texto latino do Google ML Kit para ler,
offline, o texto presente nas figuras extraídas dos PDFs.

- Documentação: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Termos: https://developers.google.com/terms
