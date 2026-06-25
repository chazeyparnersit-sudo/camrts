# StreamCam (Android · SRT nativo)

App de cámara que transmite por **SRT** a tu servidor **MediaMTX** y de ahí entra a **OBS**.
Sin marca de agua, gratis, open source (usa [RootEncoder](https://github.com/pedroSG94/RootEncoder)).

## Flujo
```
[Celular: StreamCam] --SRT--> [MediaMTX 167.233.203.172:8890] --SRT/RTSP--> [OBS Media Source]
```

## 1. Compilar la app (gratis)
1. Instala **Android Studio** (gratis).
2. Abre la carpeta `android-streamcam` como proyecto.
3. Espera a que Gradle sincronice (descarga RootEncoder desde JitPack).
4. Conecta tu celular con **Depuración USB** activada y pulsa **Run** ▶.
   - O genera el APK: menú **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

> El servidor SRT está configurado en `MainActivity.kt` (variable `srtUrl`).
> Cámbialo si usas otro host, puerto o nombre de canal.

## 2. Habilitar SRT en MediaMTX
En tu `mediamtx.yml` del servidor:
```yaml
srt: yes
srtAddress: :8890
```
Reinicia MediaMTX.

## 3. Recibir en OBS
**Fuentes → + → Origen multimedia (Media Source)**
- Desmarca *"Archivo local"*.
- En **Input** pega:
  - SRT:  `srt://167.233.203.172:8890?streamid=read:canal1`
  - o RTSP: `rtsp://167.233.203.172:8554/canal1`
- En **Input Format** (solo SRT): `mpegts`.

## Calidad
- Elige **1080p** y bitrate **8–12 Mbps** según tu subida.
- SRT recupera paquetes perdidos, así que mantiene la imagen estable
  incluso con red irregular (mucho mejor que WebRTC/WHIP).
