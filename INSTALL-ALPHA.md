# Installing Orbit (Alpha)

Orbit ships as a **debug APK** during alpha. There is no Play Store listing yet. Sideload via `adb install -r`.

---

## Section 1 — Founder install (Galaxy S24 Ultra over Linux)

> Primary device: Samsung Galaxy S24 Ultra. Build host: Linux laptop.

### One-time S24 Ultra setup

1. **Enable Developer Options**: Settings → About phone → Software information → tap **Build number** seven times.
2. **Enable USB debugging**: Settings → Developer options → **USB debugging** = ON.
3. Plug the S24 Ultra into the Linux box with the USB-C cable. Accept the **"Allow USB debugging?"** prompt on the phone (check "Always allow from this computer").
4. **AICore region quirk**: On-device Gemini Nano (used for cluster summarization) only ships in supported regions. If your S24 Ultra is on a non-US Samsung firmware, AICore may be absent and the LLM path will silently fall back to cloud routing. To verify AICore presence: `adb shell pm list packages | grep aicore`.
5. **Knox prompt**: First launch may surface a Knox/"Auto Blocker" warning since the APK isn't Play-signed. Disable Auto Blocker for the install: Settings → Security and privacy → Auto Blocker → OFF (you can re-enable after install completes).

### Download the APK from a workflow run

```bash
# Authenticate the GitHub CLI once on the Linux box.
gh auth login

# List recent runs of the build workflow, find the run id you want.
gh run list --workflow build-debug-apk.yml --limit 10

# Download the APK artifact from that run.
RUN_ID=<paste run id>
SHA=$(gh run view "$RUN_ID" --json headSha -q .headSha)
gh run download "$RUN_ID" -n "orbit-debug-${SHA}"

# Install on the connected S24 Ultra.
adb install -r app-debug.apk
```

`adb install -r` replaces a prior install in place — no need to uninstall first because every CI build is signed with the committed `app/debug.keystore`.

### Tagged release (stable URL)

When a `v*` tag is pushed, the workflow attaches `orbit-debug.apk` to a GitHub Release at `https://github.com/richelgomez99/capsule-app/releases/tag/<tag>`. From Linux:

```bash
gh release download <tag> -p orbit-debug.apk
adb install -r orbit-debug.apk
```

---

## Section 2 — Alpha cohort install

> Audience: alpha testers running macOS, Linux, or Windows.

### Prereqs

- Android phone with **Developer Options** + **USB debugging** enabled (Settings → About phone → tap Build number 7 times → back to Settings → Developer options → USB debugging ON).
- `adb` installed:
  - **macOS**: `brew install --cask android-platform-tools`
  - **Linux (Debian/Ubuntu)**: `sudo apt install android-tools-adb`
  - **Windows**: download [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools), unzip, add to PATH.

### Download from the latest Release

Get the URL of the latest `orbit-debug.apk` from the [Releases page](https://github.com/richelgomez99/capsule-app/releases). Then:

```bash
# macOS / Linux — replace <release-url> with the asset URL from the release page.
curl -L -o orbit-debug.apk <release-url>
adb install -r orbit-debug.apk
```

```powershell
# Windows PowerShell.
Invoke-WebRequest -Uri <release-url> -OutFile orbit-debug.apk
adb install -r orbit-debug.apk
```

### If Knox / Auto Blocker prompts on Samsung devices

Samsung's Auto Blocker treats sideloaded APKs as untrusted. Workaround: Settings → Security and privacy → **Auto Blocker** → toggle OFF for the install, then back ON after the app launches successfully. This is expected for any non-Play APK.

### Verifying the install worked

```bash
adb shell pm path com.capsule.app
# Should print: package:/data/app/.../com.capsule.app-.../base.apk
```

Open Orbit on the phone. If sign-in fails, the build was likely produced before `DEBUG_SUPABASE_URL` / `DEBUG_SUPABASE_ANON_KEY` were configured as repo secrets — re-run the workflow after secrets land.

---

## Section 3 — What's in this build

This file is **not** a changelog. The source of truth is:

- The most recent **status log entry** in [`specs/002-intent-envelope-and-diary/tasks.md`](specs/002-intent-envelope-and-diary/tasks.md) (Phase 11 progress).
- The commit history on `main`: `git log --oneline -20 origin/main`.
- The audit log table in the on-device database (`audit_log` rows with `action LIKE 'CLUSTER_%'` for cluster-lifecycle events).

Cross-reference those three sources to know what shipped in the APK you just installed.
