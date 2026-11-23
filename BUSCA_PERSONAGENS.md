# Pessoa 2 — Busca de personagens

A tela está em `CharacterSearchActivity`. A `MainActivity` tem um botão simples para abri-la enquanto o menu do grupo não está pronto. Para integrar ao menu definitivo:

```kotlin
startActivity(Intent(this, CharacterSearchActivity::class.java))
```

## Comportamento

- Valida `trim()`, campo vazio e mínimo de 3 caracteres antes de consultar a API.
- Faz `GET https://hogwarts-api.com/api/characters?search=...&page_size=1` em um executor fora da thread principal, com limites de conexão e leitura de 15 segundos.
- Mostra o primeiro resultado: nome, espécie e casa. Campos nulos, vazios ou ausentes aparecem como "Não informado".
- Mostra o ProgressBar durante a consulta, bloqueia buscas duplicadas e limpa detalhes antigos.
- Trata retorno vazio, falha de rede, erro HTTP e resposta inválida. Ao terminar, oculta o ProgressBar e permite tentar novamente.
- Mantém a consulta e o resultado em ViewModel durante a rotação. Não há persistência em disco.

## Ajustes de apoio

- Permissão `INTERNET` e registro da Activity no manifesto.
- `minSdk` ajustado de 24 para 29 conforme o enunciado.
- Dependências de ViewModel/LiveData e de testes no catálogo de versões.
- Configuração local do Android SDK em `local.properties`, ignorado pelo Git. Cada integrante deve configurar seu próprio SDK no Android Studio.

Menu completo e busca de criaturas continuam sendo responsabilidade das outras partes.

## Verificação

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Os testes cobrem validação sem chamada de rede, codificação do termo, primeiro resultado, campos ausentes, retorno vazio, timeout, HTTP de erro, resposta inválida, nova tentativa, estados da tela e recriação durante uma consulta. Os testes de interface usam Robolectric no SDK 29; não substituem uma conferência visual no celular.

Para a conferência no aparelho: abra o app, toque em "Buscar personagem" e tente campo vazio, `ab`, ` Weasley ` e `zzzzinexistente123`. Faça também uma busca sem internet e depois repita com internet. Gire a tela durante uma busca e após o resultado.

APK de desenvolvimento: `app/build/outputs/apk/debug/app-debug.apk`. Ele contém esta parte e o acesso provisório; não representa o trabalho completo do grupo.

Documentação da API: https://hogwarts-api.com/endpoints/characters
