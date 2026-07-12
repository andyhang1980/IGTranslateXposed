# IGTranslateXposed

Standalone [LSPosed](https://github.com/LSPosed/LSPosed) module that translates Instagram
(`com.instagram.android`) posts, comments and bios into your language.

## Features
- Hooks `TextView.setText` (and scans Instagram's dex for custom `setText` methods) so
  nearly all displayed text gets translated.
- Skips editable inputs and already-translated text; uses a memory cache + in-flight
  dedup to avoid repeated network calls and flicker.
- Backend: **OpenAI-compatible API** (DeepSeek / SiliconFlow / any custom endpoint) as
  primary, with **Google free translate** as automatic fallback.
- Simple settings UI: enable switch, provider preset (DeepSeek / SiliconFlow / Custom),
  Base URL / API Key / Model, target language.

## Build
```
./gradlew assembleDebug
```
Then install the APK and enable the module in LSPosed (scope: Instagram).

## Setup
1. Open the module's settings from the LSPosed module list.
2. Toggle **Enable**, pick a provider (or Custom), enter your API key/model.
3. Set the target language (default `zh-CN`).
4. Restart Instagram.

## Notes
- No ML Kit / Google key required. The custom LLM is the only online translator used when
  enabled; Google free API is used as fallback.
- API credentials are stored in the module's `SharedPreferences` (world-readable) so the
  hooked Instagram process can read them.
