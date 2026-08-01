# FB Notifier (nativo, Kotlin)

Wrapper de Facebook con notificaciones, sin React Native ni Expo. WebView +
WorkManager puros del SDK de Android. APK esperado: ~3-5 MB (vs ~25-60 MB
de la versión Expo).

## Por qué el build es distinto esta vez
Las herramientas oficiales del Android SDK (aapt2, d8, etc.) son binarios
compilados para Linux con glibc, y Termux usa Bionic (el libc de Android),
así que no corren directo en Termux. La solución estándar es un chroot de
Ubuntu dentro de Termux vía `proot-distro`.

## Compilar vía GitHub Actions (recomendado, evita todo el lío de proot)

Los runners de GitHub Actions ya traen Ubuntu con JDK y Android SDK
preinstalados — nada de `proot-distro`, nada de AAPT2 fallando.

1. Sube esta carpeta completa a un repositorio de GitHub (puede ser privado).
2. Ve a la pestaña **Actions** del repo → workflow "Build APK nativo (Kotlin)" → **Run workflow**.
3. Cuando termine (unos minutos), entra al run y baja hasta **Artifacts** →
   descarga `fb-notifier-apk` (es un .zip que contiene el `.apk` de debug,
   ya autofirmado, listo para instalar).
4. Pásalo a tu teléfono e instálalo (activa "orígenes desconocidos" si lo pide).

No necesitas ningún secret/token para esto — a diferencia del proyecto
Expo, aquí no depende de una cuenta externa.

## Setup (una sola vez) — build local vía Termux + proot-distro
Alternativa si prefieres compilar en el propio teléfono sin depender de
GitHub. Nota: tiene problemas conocidos con AAPT2 bajo `proot` en algunos
entornos; si te trabas ahí, usa la opción de GitHub Actions de arriba.

```bash
pkg update && pkg upgrade -y
pkg install proot-distro -y
proot-distro install ubuntu
proot-distro login ubuntu
```

Ya dentro del Ubuntu (el prompt cambia):

```bash
apt update && apt install -y openjdk-17-jdk wget unzip

# Android SDK cmdline-tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest

export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Gradle (versión compatible con Android Gradle Plugin 8.5)
cd ~
wget https://services.gradle.org/distributions/gradle-8.7-bin.zip
unzip gradle-8.7-bin.zip
export PATH=$PATH:~/gradle-8.7/bin
```

Guarda esas dos líneas de `export` en `~/.bashrc` dentro del Ubuntu para no
repetirlas cada vez:
```bash
echo 'export ANDROID_HOME=~/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:~/gradle-8.7/bin' >> ~/.bashrc
```

## Compilar el proyecto

Copia el proyecto a algún lugar accesible desde el Ubuntu (proot-distro
monta el `$HOME` de Termux normalmente en `/data/data/com.termux/files/home`
dentro del chroot también, así que puedes trabajar directo ahí):

```bash
cd ~   # dentro del Ubuntu, tu $HOME de Termux
cd fb-notifier-native   # donde hayas extraído este proyecto
gradle assembleDebug
```

Usamos `assembleDebug` (no `assembleRelease`) a propósito: para uso
personal no necesitas firmar con un keystore de producción, el build debug
ya viene firmado automáticamente con una clave de debug y se instala sin
problema.

El APK queda en:
```
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Sal del chroot (`exit`), copia el APK a un lugar accesible desde Android
normal, e instálalo:
```bash
exit   # vuelves a Termux normal
cp $(proot-distro login ubuntu -- find /root -name "*debug.apk" 2>/dev/null | head -1) ~/storage/downloads/
```
(o más simple: desde dentro del chroot, copia el APK directo a
`~/storage/downloads/` si esa ruta es visible ahí; si no, usa un explorador
de archivos para moverlo desde la carpeta del proyecto).

## Diferencias funcionales vs la versión Expo
- Misma lógica: WebView de login → extrae cookies → guarda perfil →
  WorkManager revisa cada ~15 min (mínimo real de Android, no cambia
  aunque sea nativo) → notificación local si sube algún contador.
- Multi-cuenta: igual, WebView se limpia de cookies entre cada login nuevo.
- Sin librerías de terceros: nada de OkHttp, Retrofit, Compose,
  RecyclerView — solo AndroidX core + WorkManager. Esto es lo que
  mantiene el tamaño bajo.
- El ícono es un vector XML simple (no un diseño final); reemplázalo
  cuando quieras en `res/drawable/ic_launcher_foreground.xml`.

## Limitaciones (las mismas de siempre, no cambian por ser nativo)
- No es push instantáneo: WorkManager con `PeriodicWorkRequest` no puede
  bajar de ~15 min, es un límite del sistema operativo, no de la
  tecnología usada.
- El parser de `m.facebook.com` sigue siendo HTML parseado por regex; si
  Facebook cambia esa estructura, hay que ajustar `FacebookPoller.kt`.
