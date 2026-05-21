# 05 — Camera & Media Agent

**Місія**: CameraX-пайплайн, JPEG-компресія, файлове сховище фото, прив'язка до станції/зони.

## Контекст
- Бізнес-правила фото: [.claude/business_logic.md](../.claude/business_logic.md).

## Файли
```
camera/
├── CameraXController.kt    ← bind preview/imageCapture, flash, zoom, takePicture
└── ImageCompressor.kt      ← JPEG-resize/compress перед збереженням

data/storage/FileStorageManager.kt   ← шляхи, write, delete фото

ui/inspection/
├── CameraScreen.kt
├── GalleryScreen.kt
└── ZoneInspectionViewModel.kt

data/local/entity/PhotoEntity.kt
data/local/dao/PhotoDao.kt
domain/model/Models.kt              ← Photo, Zone, PhotoCategory
```

## Пайплайн (короткий)
1. `CameraXController.takePicture()` → temp JPEG.
2. `ImageCompressor.compress(file, ...)` → resize + quality.
3. `FileStorageManager.savePhoto(stationId, zone, category, bytes)` → постійний файл.
4. `PhotoDao.insert(PhotoEntity(...))` — рядок у БД.
5. UI оновлюється через `Flow` від `PhotoDao`.

## 3 зони + 2 категорії
- Зони: **Nádraží / Čekárna / WC** (`Zone` enum, `domain/model/Enums.kt`).
- Категорії: **normal photo** vs **defect** (`PhotoCategory`). Окремі лічильники в UI.

## Що покращити
- Винести magic-числа (target width, quality) в `ImageCompressor` як константи / параметри.
- EXIF — переконатися, що orientation коректно зберігається після ресайзу.
- Великі black-box ділянки `takePicture` callback — обернути в `suspendCancellableCoroutine` + sealed result.
- Обробка `PERMISSION_DENIED` — фолбек UI замість мовчазного провалу.

## Експорт залежить від цього шару
ZIP-структура (див. [06-export-background.md](06-export-background.md)) будується з `PhotoEntity.filePath`. Перейменування / реструктуризація сховища = ламає експорт.

## Готово, коли
- Усі `Photo*` шляхи централізовано в `FileStorageManager`.
- Видалення станції чистить файли (а не тільки рядки) — перевірити в `StationRepositoryImpl.clearAll()`.
