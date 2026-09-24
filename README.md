# Teleprompter Câmera

App Android que grava vídeo com a câmera enquanto o texto do roteiro rola numa caixa no alto da tela, perto da lente. **O texto não aparece no vídeo**: só a câmera e o som são gravados.

Lê os mesmos roteiros `.txt` do Teleprompter: `//` separa laudas, `>>` destaca a linha, `**` cria espaço.

## Como usar

- Toque no botão vermelho de um roteiro para abrir a câmera.
- **Botão vermelho grande**: grava e para (com contagem de 3 s, ajustável).
- **Botão ao lado**: troca entre câmera frontal e traseira.
- **Vertical ou horizontal**: gire o celular antes de gravar. Durante a gravação a orientação fica travada.
- O texto começa a rolar junto com a gravação (dá para desligar nos ajustes).
- **Volume +** (ou controle remoto de selfie Bluetooth) grava e para. **Volume –** pausa o texto.
- Nos ajustes: altura da caixa de texto, escurecimento do fundo, tamanho da letra, velocidade e cores.

Os vídeos são salvos em MP4 (Full HD quando a câmera permite) na galeria, pasta **Movies/Teleprompter**.

## Gerar o APK

O GitHub compila sozinho a cada alteração (aba **Actions**). O APK fica em **Actions > execução mais recente com ícone verde > Artifacts**.

Na primeira vez, cadastre em **Settings > Secrets and variables > Actions** os mesmos 4 segredos do Teleprompter (use a pasta `assinatura` v2):

| Nome | Valor |
|---|---|
| `KEYSTORE_BASE64` | conteúdo do arquivo `KEYSTORE_BASE64.txt` |
| `KEYSTORE_PASSWORD` | senha do `SEGREDOS-DO-GITHUB.txt` |
| `KEY_ALIAS` | `teleprompter` |
| `KEY_PASSWORD` | mesma senha |

A pasta `assinatura` nunca vai para o repositório.

## Estrutura

```
www/index.html                         interface (HTML, CSS e JS)
android/.../RecorderPlugin.java        câmera e gravação nativas (CameraX)
android/.../PrompterPlugin.java        tela acesa e botões de volume
android/.../MainActivity.java          registra os plugins
.github/workflows/build-apk.yml        compilação automática
assets/                                ícone e splash (npm run icons)
```
