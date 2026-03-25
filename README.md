# NPCoo (Forge Mod)

This project is a Minecraft Forge mod workspace configured for:
- Minecraft: `1.21.11`
- Forge: `1.21.11-61.1.0`
- Java: `21`

## Prerequisites

- Java 21 installed
- Git (optional, for cloning)
- Minecraft Launcher installed

## 1) Install Forge for this project

This project already declares Forge in `build.gradle`:
- `net.minecraftforge:forge:1.21.11-61.1.0`

You do not need to manually add Forge to Gradle files unless you want to change versions.

### Windows

1. Open PowerShell in this project folder.
2. Run:

```powershell
.\gradlew.bat --refresh-dependencies
.\gradlew.bat genIntellijRuns
```

### Apple (macOS)

1. Open Terminal in this project folder.
2. Run:

```bash
chmod +x ./gradlew
./gradlew --refresh-dependencies
./gradlew genIntellijRuns
```

That downloads Forge dependencies and creates run configs for the dev environment.

## 2) Add Minecraft 1.21.11 if it is not installed

Forge and the launcher need the matching vanilla Minecraft version files.

### Windows and Apple (same steps)

1. Open Minecraft Launcher.
2. Go to **Installations**.
3. Click **New installation**.
4. In **Version**, choose `release 1.21.11`.
5. Create and run it once.

If you do not see `1.21.11`, update the launcher and check again. If the version still does not appear, use the latest `1.21.x` available and then align your `build.gradle` versions to match.

## 3) Add the OpenAI API key (OPENAI_API_KEY)

This mod uses `OpenAIOkHttpClient.fromEnv()`, so it reads the API key from an environment variable named `OPENAI_API_KEY`.

### Windows (PowerShell)

Set it for your current session:

```powershell
$env:OPENAI_API_KEY="your_api_key_here"
```

Set it permanently for your user account:

```powershell
[System.Environment]::SetEnvironmentVariable("OPENAI_API_KEY", "your_api_key_here", "User")
```

Restart your terminal and IDE after setting a permanent variable.

### Apple (macOS)

For zsh (default on modern macOS):

```bash
echo 'export OPENAI_API_KEY="your_api_key_here"' >> ~/.zshrc
source ~/.zshrc
```

For bash:

```bash
echo 'export OPENAI_API_KEY="your_api_key_here"' >> ~/.bash_profile
source ~/.bash_profile
```

Restart your terminal and IDE after changes.

## Quick verification

Run a dev client once setup is done:

### Windows

```powershell
.\gradlew.bat runClient
```

### Apple (macOS)

```bash
./gradlew runClient
```

If dependencies and environment variables are set correctly, the game should launch with your mod and OpenAI calls should no longer fail due to missing API key.
