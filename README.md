# SmartBot (Android)

A Jarvis-style AI assistant app with a persistent on-device "brain" that
remembers facts from your conversations.

## What it does
- Chat screen with an animated core and a boot sequence, Jarvis-style
- Connects to any AI backend you configure — not locked to one provider
- After each reply, SmartBot can quietly save a fact worth remembering —
  view/clear them anytime in the 🧠 Brain panel
- Everything is stored locally on your device (SharedPreferences) — nothing
  is synced anywhere except the message text sent to whichever backend
  you've chosen, to get a reply

## Connections (⚙️ Settings)
SmartBot doesn't lock you to one AI backend — it ships with three starter
slots you can edit, and you can add more:

- **Anthropic** — talks to `api.anthropic.com` directly. Needs a key from
  https://console.anthropic.com
- **FreeLLMAPI** (or OpenRouter, Groq, together.ai, LM Studio, etc.) — any
  endpoint that speaks the OpenAI `/chat/completions` schema. Set Base URL
  to that service's `/v1`, paste its key, and set the model name it expects.
- **Local** — point this at a model running on your own network (e.g.
  Ollama at `http://localhost:11434/v1`, or any self-hosted OpenAI-compatible
  server). No API key needed for most local setups. True fully-offline,
  on-device inference (no server at all) needs a dedicated runtime like
  MLC-LLM or llama.cpp's Android bindings with a model file bundled in —
  that's a separate, heavier integration this project doesn't include yet,
  but the provider slot is ready for it once you have one running.

Pick which connection is active with the radio button; tap a row to edit
it, "+ Add connection" for more. Everything is stored only on your device.

## How to build the APK — no Android Studio needed
This project includes a GitHub Actions workflow that builds the APK on
GitHub's own servers. You just need a (free) GitHub account.

1. Go to https://github.com/new, create a new repository (any name, e.g.
   `smartbot`), keep it empty (no README/license)
2. Upload this whole `SmartBot/` folder's contents to it. Easiest way with
   no command line: on the new repo's page, click "uploading an existing
   file" and drag in everything from this unzipped folder (make sure
   `.github/workflows/build-apk.yml` comes along — some file pickers hide
   dotfiles, so check it's there after uploading, or use GitHub Desktop
   instead: https://desktop.github.com, which shows all files)
3. Once pushed, click the **Actions** tab on your repo — a workflow run
   should already be in progress (or click "Run workflow" if not)
4. Wait for it to finish (a few minutes, green checkmark)
5. Click into the finished run → scroll to **Artifacts** → download
   `SmartBot-debug-apk` — that's a zip containing your installable
   `app-debug.apk`

Then on your phone: download that APK (e.g. via a link to yourself, or
Google Drive), tap it in your Files/Downloads app, allow "install from
this source" when prompted, and it installs.

### Alternative: command line, no Android Studio, on your own computer
If you'd rather build locally without the Android Studio IDE:
1. Install just the JDK 17 and the Android **command-line tools** (not
   full Studio): https://developer.android.com/studio#command-tools
2. Set `ANDROID_HOME` to point at them, then from this project folder run:
   ```
   gradle :app:assembleDebug
   ```
3. The APK lands at `app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions route above is simpler since it needs nothing installed
on your machine at all.

## Folder structure
```
SmartBot/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/smartbot/app/
│       │   ├── MainActivity.kt   (UI)
│       │   ├── Brain.kt          (persistent memory + saved connections)
│       │   └── Providers.kt      (pluggable LLM backends)
│       └── res/values/
├── build.gradle.kts
└── settings.gradle.kts
```

## Honest limits
This app can only access what Android grants it permission for — nothing
on a phone (native or web) gets blanket "access to everything," no matter
which AI backend is behind it. As built, it only uses internet access to
reach whichever provider you've configured. If you want it to read SMS,
contacts, camera, etc., those are separate Android permissions you'd add
to the manifest and request from the user explicitly, one at a time — and
each one makes the app a harder sell for anyone you share the APK with,
since it'll look suspicious without being published through Play Store
review.
