# ARCHIEL

Telefondan **opencode** oturumlarını yöneten Android asistan uygulaması. Kotlin + Jetpack Compose,
backend olarak opencode'un HTTP API'si (`/api/*`, SSE akış).

## Tasarım

Konu cümlesi: *bir mühendis panosu — kömür grisi zemin, tek amber sinyal, düz yüzeyler, ölçülü tipografi.*

Referans: `lunanoir21/spec-mockup-design` becerisi ve `lunanoir21/desktop-widget-control` (`#11120e`
grafit + `#e0a458` amber) projesi. Token'lar elle seçilmedi, becerinin kendi üreticileriyle üretildi:

```
python3 scripts/palette.py --hue 75 --mood crisp --scheme mono --name amber --css design/palette.css
python3 scripts/scales.py --target mobile --density normal --css design/scales.css
```

| | koyu | açık |
|---|---|---|
| zemin / yüzey / çizgi | `#100F0E` `#161513` `#2A2928` | `#F5F3F1` `#FBF9F7` `#D9D7D5` |
| metin / ikincil | `#F0EEEC` `#A09E9B` | `#131110` `#6B6866` |
| vurgu (tek sinyal) | `#DB9E3D` | `#7E5403` |

- Yazı: **Instrument Sans** (gövde), **Syne** (başlık/rakam), **DM Mono** (kod ve araç çıktısı) —
  referans projenin kendi fontları, OFL lisanslı (`res/font/`).
- Ölçek 4 px tabanlı: 4 / 8 / 12 / 16 / 24 / 32. Yarıçap 5 / 10 / 16.
- Ayırma tek araçla: saç teli (1 px `outline`). Gölge, gradyan, parlama yok. Emoji yok.
- Ses: Türkçe, cümle biçiminde; tarihler `13 Eki 2026 · 14:32`.

## Sunucu

Uygulama sadece bir adres ve parola ister; ikisi de Ayarlar ekranından değişir.

```sh
# masaüstü — telefon aynı Wi-Fi'dan bağlanır
opencode serve --hostname 0.0.0.0 --port 4096
# çıktıdaki "server password ..." satırı paroladır

# ya da sabit parola
OPENCODE_SERVER_PASSWORD=... opencode serve --hostname 0.0.0.0 --port 4096
```

| Nerede | Adres |
|---|---|
| Telefon (Termux'ta `opencode serve`) | `http://127.0.0.1:4096` |
| Masaüstü, aynı Wi-Fi | `http://192.168.x.x:4096` |
| Android emülatör | `http://10.0.2.2:4096` (varsayılan) |

## Derleme

Bu depodaki GitHub Actions iş akışı birim testlerini çalıştırır, `assembleDebug` yapar ve APK'yı
artifact olarak yükler: **Actions → build → Artifacts → archiel-debug**.

Android Studio'da doğrudan da açılabilir (JDK 17, Gradle wrapper dahil).

## Yapı

```
app/src/main/java/dev/scdeal/archiel/
  OpenCodeClient.kt   HTTP + SSE istemcisi, veri modelleri
  ChatViewModel.kt    oturum listesi, akış durumu, hata durumu
  MainActivity.kt     üç ekran: oturumlar / sohbet / bağlantı
  Theme.kt            palet, tipografi, ölçek
  Cfg.kt              sunucu adresi + parola (SharedPreferences)
```

## Sürüm 1'in sınırları

Düz metin (Markdown/ sözdizimi renklendirme yok), `reasoning` parçaları gizli, araç çıktısı
salt-okunur, izin (permission) onay ekranı ve dosya ekleme yok. Parola düz SharedPreferences'ta
tutuluyor — cihazı ele geçeren biri sunucuya girebilir.
