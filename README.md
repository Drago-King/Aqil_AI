# Aqil AI — your personal Android phone agent

Aqil AI is an Android app that does things on your phone when you ask — by text or voice.
Tell it *"open WhatsApp and message Jihan"* and it reads the screen, taps, types and
scrolls for you. It talks back with ElevenLabs (or the phone's built-in voice), works with
any OpenAI-compatible model (OpenRouter, OpenAI, Groq, Together…), and has a floating
bubble so you can summon it from anywhere.

> **Honest note.** This is a personal-use build, not a Play Store app. The automation uses
> Android's Accessibility system — powerful but imperfect. Simple flows (open app, search,
> tap, type, send) work well; some apps (banking, parts of WhatsApp) resist automation, so
> expect to iterate on trickier tasks. That's normal for on-device agents.

---

## 1) Build the app on GitHub (no PC needed)

The project builds itself in the cloud and hands you an installable `.apk`.

1. Sign in at **github.com**.
2. **New repository** → name it `aqil-ai` → **Create repository**.
3. Upload the project: **Add file → Upload files**, then drag in **all** the files and
   folders from this project (keep the folder structure). Commit to the **main** branch.
   - Easiest on a phone: install the **GitHub** app, or use the website's upload button.
4. The build starts automatically. Open the **Actions** tab: a yellow dot = building,
   green tick = done. First build takes ~5–8 minutes.
5. When it's green, open **Releases** (right side of the repo home page). You'll see
   **Aqil AI v1.0.x** with **AqilAI.apk** attached.
6. On your phone, open that release, tap **AqilAI.apk** to download, then open it to
   install. Android will ask you to **allow installing from unknown sources** — allow it.

Re-run any time from **Actions → Build Aqil AI APK → Run workflow**, or just push a change.

---

## 2) First-time setup (in the app)

Open Aqil AI → **Settings**:

- **Screen control** → *Enable*. This opens Android's Accessibility list — turn on
  **Aqil AI Agent**. This is what lets Aqil tap and type. (Android shows a scary warning
  because this permission is powerful; that's expected for an automation app.)
- **Floating bubble** → *Allow*, then *Show bubble*. Grants "display over other apps" and
  drops a gold bubble on screen. Tap it anywhere to give a voice command; drag to move it.
- **Microphone**: the first time you tap the mic, allow the permission.

**AI models & keys**
- Pick a model with the circle on the left. Tap the pencil to edit **Base URL / Model /
  API key**, or **Add model / base URL** for more.
- Get a free OpenRouter key at **openrouter.ai/keys** and paste it into the profile whose
  base URL is `https://openrouter.ai/api/v1`. Starter models are pre-filled (including the
  `tencent/hy3:free` one from your screenshot).
- Any OpenAI-compatible endpoint works — just set its base URL, model name and key.

**Voice (ElevenLabs)** — optional
- Paste your **ElevenLabs API key** and **Voice ID** (from your ElevenLabs Voices page),
  keep **Model ID** as `eleven_turbo_v2_5` (or change it), tap **Save voice**, then
  **Test**. No key? Aqil still speaks using the phone's built-in voice.

---

## 3) Using it

- **Type** a task in Assistant and hit send, or tap the **mic** and speak it.
- Tap the **floating bubble** from any app to speak a command without switching back.
- Watch the grey action chips (`launch_app → opened WhatsApp`, `tap → clicked Search`…) to
  see what Aqil is doing step by step.

Try: *"open the calculator"* · *"open WhatsApp and search for Jihan"* ·
*"open settings and turn on Bluetooth"* · *"open Spotify"*.
Then build up to the multi-step ones.

---

## 4) How it works (for when you want to extend it)

Aqil runs a simple **reason–act loop** (`ai/AgentEngine.kt`):

1. Read the screen — the accessibility tree is serialized into a numbered list of elements
   with text + coordinates (`agent/ScreenReader.kt`). This is text, so even cheap/free
   models can drive the phone (no vision model required).
2. Ask the model for the **single next action** as JSON, e.g.
   `{"action":"tap","params":{"text":"Search"}}`.
3. Execute it via the accessibility service (`agent/AqilAccessibilityService.kt`) — taps,
   typing, scrolling, back/home/recents, screenshots, launching apps by name.
4. Re-read the screen and repeat until the model says `{"action":"finish"}`.

Good places to grow it:
- Add actions in `AqilAccessibilityService.execute()` and document them in the
  `SYSTEM_PROMPT` inside `AgentEngine.kt` (e.g. long-press, copy/paste, WhatsApp-specific
  helpers, dialing a number).
- Swap in a vision model and send screenshots for apps with poor accessibility labels.
- Add more starter models in `data/Models.kt → ModelProfile.defaults()`.

---

## 5) Troubleshooting

- **"Add an API key in Settings first."** → the selected model has no key. Edit it.
- **Model error 401/403** → wrong or missing key for that provider.
- **Aqil replies but doesn't tap** → Screen control (Accessibility) isn't on, or was turned
  off by the system. Re-enable it in Settings.
- **Bubble missing** → allow "display over other apps", then tap *Show bubble*.
- **Build fails in Actions** → open the failed step's log; it almost always names the file
  and line. Fix, commit, and it rebuilds.

---

## Project layout

```
app/src/main/java/com/aqil/ai/
  MainActivity.kt          permissions, mic, Compose host
  MainViewModel.kt         glue: settings + agent + voice + chat state
  ai/                      OpenAiClient, ElevenLabsClient, AgentEngine (the loop)
  agent/                   AccessibilityService, ScreenReader, action models
  voice/                   SpeechInput (mic), VoicePlayer (ElevenLabs + fallback)
  overlay/                 FloatingBubbleService
  data/                    settings storage + model profiles
  ui/                      Compose screens (chat, settings) + theme
.github/workflows/build.yml   cloud build → APK → GitHub Release
```

Built for you. Tweak the system prompt, add actions, make it yours.
