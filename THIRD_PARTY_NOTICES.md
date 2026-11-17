# Avisos y Licencias de Terceros (Third-Party Notices)

**Terminal-IA** se distribuye bajo la **Licencia MIT** (ver [`LICENSE`](LICENSE)).  
A continuación se detallan las bibliotecas de terceros incluidas o enlazadas durante la compilación en `app/build.gradle.kts`, junto con sus licencias compatibles.

---

## Dependencias de ejecución incluidas en la aplicación

| Componente / Coordenada Maven | Versión | Licencia | Notas de atribución |
| :--- | :--- | :--- | :--- |
| **`com.github.mwiede:jsch`** | `2.28.2` | **BSD-3-Clause** (JSch License) | Fork mantenido de JSch por Matthias Wiedemann (`Copyright (c) 2002-2015 Atsuhiko Yamanaka, JCraft, Inc.` y contribuidores de `mwiede/jsch`). Cliente SSH Ed25519/TOFU. |
| **`org.bouncycastle:bcprov-jdk18on`** | `1.85` | **Bouncy Castle License** (estilo MIT) | `Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc.` Usado para generación de certificados X.509 autofirmados (HTTPS opcional) y soporte criptográfico Ed25519. |
| **`org.connectbot:termlib`** | `0.0.27` | **Apache License 2.0** | `Copyright ConnectBot Contributors`. Emulador de terminal VT/ANSI para Jetpack Compose. |
| **`com.squareup.okhttp3:okhttp`** & **`logging-interceptor`** | `4.10.0` | **Apache License 2.0** | `Copyright Square, Inc.` Cliente HTTP/HTTPS para consultas a proveedores de IA configurados. |
| **`com.squareup.retrofit2:retrofit`** & **`converter-moshi`** | `2.12.0` | **Apache License 2.0** | `Copyright Square, Inc.` |
| **`com.squareup.moshi:moshi-kotlin`** & **`moshi-kotlin-codegen`** | `1.15.2` | **Apache License 2.0** | `Copyright Square, Inc.` |
| **AndroidX / Jetpack** (`core-ktx`, `activity-compose`, `compose-*`, `material3`, `material-icons-*`, `lifecycle-*`, `room-*`) | BOM `2024.09.00` / Room `2.7.0` | **Apache License 2.0** | `Copyright The Android Open Source Project` |
| **Kotlin & `kotlinx-coroutines`** (`android`, `core`) | Kotlin `2.2.10` / Coroutines `1.10.2` | **Apache License 2.0** | `Copyright JetBrains s.r.o. and Kotlin Programming Language contributors` |
| **Firebase Android SDK** (`firebase-bom`, `firebase-ai`, `firebase-appcheck-*`) | BOM `34.17.0` | **Apache License 2.0** | `Copyright Google LLC` |

---

## Dependencias solo de pruebas (`testImplementation` / `androidTestImplementation`)

Estas bibliotecas no forman parte del APK de producción y se utilizan exclusivamente para pruebas unitarias y Robolectric:

| Componente | Versión | Licencia |
| :--- | :--- | :--- |
| **`org.robolectric:robolectric`** | `4.16.1` | **MIT License** |
| **`junit:junit`** | `4.13.2` | **Eclipse Public License 1.0 (EPL-1.0)** |
| **`org.jetbrains.kotlinx:kotlinx-coroutines-test`** | `1.10.2` | **Apache License 2.0** |
| **AndroidX Test** (`core`, `junit`, `runner`, `espresso-core`, `ui-test-junit4`) | `1.6.1` / `1.3.0` / `3.7.0` | **Apache License 2.0** |

---

## Aviso sobre Termux (`com.termux`)

**Termux** (distribuido bajo **GPLv3** en F-Droid y GitHub Releases por sus propios mantenedores) **NO está incluido, empaquetado ni enlazado dentro del APK de Terminal-IA**.  
Termux se instala por separado como aplicación independiente del usuario, y Terminal-IA se comunica con él mediante el protocolo estándar SSH sobre loopback local (`127.0.0.1`).
