# Contributing to Acode

Thank you for your interest in contributing to Acode! This guide will help you get started with development.

## Quick Start Options

### Option 1: DevContainer (Recommended)

1. Install the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) in VS Code or other editors that support [DevContainers](https://containers.dev/).

2. Clone and open the repository:
   ```bash
   git clone --recurse-submodules https://github.com/Acode-Foundation/Acode.git
   code Acode
   ```

3. When VS Code prompts "Reopen in Container", click it
   - Or use Command Palette (Cmd/Ctrl+Shift+P) → "Dev Containers: Reopen in Container"

4. Wait for the container to build (~5-10 minutes first time, subsequent opens are instant)

5. Once ready, build the APK:
   ```bash
   npm run build -- dev apk
   ```

   > Use any package manager (pnpm, bun, npm, yarn, etc.)

### Option 2: Docker CLI (For Any Editor)

> [!NOTE]
> If you try to use Podman, Kindly note that it would not work properly until https://github.com/containers/buildah/pull/5845 is merged/implemented in Podman.

If your editor doesn't support DevContainers, you can use Docker directly:

```bash
# Clone the repository
git clone --recurse-submodules https://github.com/Acode-Foundation/Acode.git
cd Acode

# Build the Docker image from our Dockerfile
docker build --target standalone -t acode-dev .devcontainer/

# Run the container with your code mounted
docker run -it --rm \
  -v "$(pwd):/workspaces/acode" \
  -w /workspaces/acode \
  acode-dev \
  bash

# Inside the container, install dependencies and build
npm ci
npm run build -- dev apk
```

**Keep container running for repeated use:**
```bash
# Start container in background
docker run -d --name acode-dev \
  -v "$(pwd):/workspaces/acode" \
  -w /workspaces/acode \
  acode-dev \
  sleep infinity

# Execute commands in the running container
docker exec -it acode-dev bash -c "npm ci"
docker exec -it acode-dev npm run build -- dev apk

# Stop and remove when done
docker stop acode-dev && docker rm acode-dev
```

---

## 🛠️ Manual Setup (Without Docker)

If you prefer not to use Docker at all:

### Prerequisites

| Requirement | Version |
|------------|---------|
| **Node.js** | 24 LTS |
| **npm** | Included with Node.js |
| **Java JDK** | 27 (Eclipse Temurin) |
| **Android SDK** | API 36 | 
| **Gradle** | 9.8.0-rc-3 (included wrapper) |

### Environment Setup

Add these to your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.config/fish/config.fish`):

**macOS:**
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

**Linux:**
```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

Set `JAVA_HOME` to your Eclipse Temurin JDK 27 installation and add `$JAVA_HOME/bin` to `PATH`. The checked-in daemon criteria require Temurin 27, and the Gradle wrapper downloads the required Gradle version automatically. Android Gradle Plugin 9.4.1 supplies built-in Kotlin support.

Java 27 support currently requires [Gradle 9.8](https://docs.gradle.org/9.8.0-rc-3/release-notes.html), whose latest release is RC3 as of 2026-09-23. Gradle 9.7.1 is the latest stable release but does not support Java 27. The wrapper pins and verifies the RC3 distribution. Java and Kotlin still emit Java 17 bytecode for Android compatibility; the build itself runs on Java 27.

### Build Steps

```bash
# Clone the repository
git clone --recurse-submodules https://github.com/Acode-Foundation/Acode.git
cd Acode

# Install dependencies
npm ci

# Build the APK
npm run build -- dev apk
```

The APK will be at: `platforms/android/app/build/outputs/apk/<edition>/debug/app-<edition>-debug.apk`

> [!NOTE]
> `@codemirror/lsp-client` comes from the `codemirror-lsp-client` git submodule and is installed as a local `file:` dependency, so initialize the submodule before running `npm ci` — see [Troubleshooting](#-troubleshooting).

## Native Android development

`platforms/android` is checked-in source: edit it directly in Android Studio. There is no platform generation or native plugin installation step.

- `platforms/android/app/src/main/java`: Acode runtime and shared native services.
- `platforms/android/app/src/free`: advertising implementation and metadata.
- `platforms/android/app/src/store`: billing and proot assets, excluded by `fdroid`.
- `src/native`: typed native APIs imported by `src/native/index.ts`; `bridge(service)` binds promise-based actions to the shared transport.
- `src/platforms/android` and `src/platforms/ios`: platform transports using the shared callback and binary protocol.
- `platforms/ios`: retained iOS template for the future Acode port; Android services are not yet ported to Swift.
- `platforms/android/app/src/main/assets/services.json`: native service registration.
- `package.json`: app ID (`name`), version and Android version code.

The native APIs are available through `Bridge.exec`, `Bridge.file`, `Bridge.http`, `Bridge.clipboard` and `Bridge.websocket`. App source uses these APIs or ordinary imports. For existing third-party plugins, `src/native/pluginCompatibility.js` exposes the legacy `cordova` namespace and module names for the public native APIs, forwarding to the same implementations. Existing direct globals and `deviceready`, pause/resume and hardware-button events remain available. Keep compatibility aliases in that file; do not use them inside Acode or add Cordova dependencies. Advertising and billing APIs retain their build-edition restrictions.

Set `package.json.name` before building or starting development:

| `name` | Edition |
| --- | --- |
| `com.foxdebug.acode` | Paid, without AdMob |
| `com.foxdebug.acodefree` | Free, with AdMob |

The scripts and Android Studio read this name; there is no free/paid command argument.
After changing the name, restart `npm run dev`. Only the selected Gradle flavor is enabled.
Use `npm run build` to refresh web assets before building directly in Android Studio, which uses the last compiled web bundle.
`npm run dev` hot-reloads JavaScript through Rspack and rebuilds the app when tracked Android source changes.
Startup probes the dev server with Proteus's three-second timeout and loads its
scripts when reachable, or uses the APK's bundled assets when unavailable. The
page stays at `https://localhost` so API CORS permissions, cookies and local
storage keep the same origin. Lazy-loaded assets use the loaded bundle's URL.
Stop and reopen the app after disconnecting the server to use the bundled build.
Gradle only compiles native source and packages the compiled web assets; no Java/Kotlin source is copied or generated by project scripts.
Paid builds exclude the AdMob native sources, Google ads/consent SDKs, manifest entries,
and JavaScript bridge. The editor uses small inactive ads APIs in paid builds, so
AdMob initialization, consent and rewarded-ad implementation are not bundled either.
Shared billing and proot remain available unless `fdroid` is requested.

```bash
npm run build -- dev apk
npm run build -- prod bundle
npm run build -- dev apk fdroid
npm run start -- android d
npm run dev -- android --target=DEVICE_SERIAL
npm test
npm run test:android
npm run typecheck
```

Release signing still reads the ignored `build.json` and keystore. Rspack compiles the native JavaScript APIs alongside the editor; there is no separate plugin build, installation, copying or source-generation command.

The familiar APK/AAB paths remain available under `platforms/android/app/build/outputs/apk/{debug,release}` and `outputs/bundle/release`.

`node dev/storage_manager.mjs y` or `n` toggles all-files access in the tracked Android manifest for the next build. Build scripts read package identity without rewriting it or reinstalling plugins.

See [the migration verification record](docs/native-migration.md) for behavior coverage and remaining device checks.

## 🔧 Troubleshooting

### Missing local dependency

`@codemirror/lsp-client` comes from the `codemirror-lsp-client` git submodule.
If dependency installation fails because it is missing, initialize it first:

```bash
git submodule update --init --recursive
npm ci
```

## 📝 Contribution Guidelines

### Before Submitting a PR

1. **Fork** the repository and create a branch from `main`
2. **Make changes** - keep commits focused and atomic
3. **Check code quality:**
   ```bash
   npm run check
   ```
4. **Test** on a device or emulator if possible

### Pull Request Checklist

- [ ] Clear description of changes
- [ ] Reference to related issue (if applicable)
- [ ] Screenshots/GIFs for UI changes
- [ ] Passing CI checks

### Code Style

We use [Biome](https://biomejs.dev/) for linting and formatting:
- Run `npm run check` before committing
- Install the Biome VS Code extension for auto-formatting

### Commit Messages

Use clear, descriptive messages:
```
feat: add dark mode toggle to settings
fix: resolve crash when opening large files
docs: update build instructions
refactor: simplify file loading logic
```

## 🌍 Adding Translations

1. Create a JSON file in `src/lang/` (e.g., `fr-fr.json` for French)
2. Add it to `src/lib/lang.js`
3. Use the translation utilities:
   ```bash
   npm run lang add       # Add new string
   npm run lang remove    # Remove string
   npm run lang search    # Search strings
   npm run lang update    # Update translations
   ```

## ℹ️ Adding New Icons (to the existing font family)
> [!NOTE]
> Acode uses SVG and converts them into a font family, to be used inside the editor and generally for plugin devs.
> 
> **Plugin-specific icons SHOULD NOT be added into the editor. Only generally helpful icons SHOULD BE added**

Many font editing software and web-based tools exist for this purpose. Some of them are listed below.

| Name | Platform |
|------|----------|
| https://icomoon.io/ | Free (Web-Based, PWA-supported, Offline-supported) |
| https://fontforge.org/ | Open-Source (Linux, Mac, Windows) |

### Steps in Icomoon to add new Icons

1. Download the `code-editor-icon.icomoon.json` file from https://github.com/Acode-Foundation/Acode/tree/main/dev
2. Go to https://icomoon.io/ > Import
3. Import the `code-editor-icon.icomoon.json` downloaded (in step 1)
4. All icons will be displayed after importing.
5. Import the SVG icon created/downloaded to be added to the Font Family.
6. On the right side, press **enable Show Characters** & **Show Names** to view the Unicode character & Name for that icon.
7. Provided the newly added SVG icon with a name (in the name box).
8. Repeat Step 5 and Step 7 until all needed new icons are added.
9. Press the export icon from the top left-hand side.
10. Press the download button, and a zip file will be downloaded.
11. Go to the Projects section of [icomoon](https://icomoon.io/new-app), uncollapse/expand the Project named `code-editor-icon`  and press the **save** button (this downloads the project file named: `code-editor-icon.icomoon.json`)

### Updating Project files for Icon Contribution
1. Extract the downloaded zip file; navigate to the `fonts` folder inside it.
2. Rename `code-editor-icon.ttf` to `icons.ttf`.
3. Copy & paste the renamed `icons.ttf` into https://github.com/Acode-Foundation/Acode/tree/main/src/res/icons
4. Copy and paste the `code-editor-icon.icomoon.json` file (downloaded in the adding icons steps) onto https://github.com/Acode-Foundation/Acode/tree/main/dev (yes, replace it with the newer one; we downloaded!)
4. Commit the changes **ON A NEW branch** (by following: [Commit Messages guide](#commit-messages))

## 🔌 Plugin Development

To create plugins for Acode:
- [Plugin Starter Repository](https://github.com/Acode-Foundation/acode-plugin)
- [Plugin Documentation](https://docs.acode.app/)
