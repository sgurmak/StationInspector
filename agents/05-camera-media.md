# 05 — Camera & Media Agent

**Місія**: CameraX-пайплайн, JPEG-компресія, файлове сховище фото, прив'язка до станції/зони.

## Контекст
- Бізнес-правила фото: [.claude/business_logic.md](../.claude/business_logic.md).

## Файли
```
camera/
├── CameraXController.kt    ← bind preview/imageCapture, flash, takePicture, stopCamera
└── ImageCompressor.kt      ← JPEG-resize/compress перед збереженням

data/storage/FileStorageManager.kt   ← шляхи, write, delete фото, clearAllPhotoFiles()

ui/inspection/
├── CameraScreen.kt
├── GalleryScreen.kt
└── ZoneInspectionViewModel.kt

data/local/entity/PhotoEntity.kt
data/local/dao/PhotoDao.kt
domain/model/Enums.kt               ← PhotoZone, PhotoCategory
domain/model/Models.kt              ← Photo
```

## Конфігурація CameraX (важливо)
- Контролер біндить **лише `Preview` + `ImageCapture`** ([CameraXController.kt:51-61](../app/src/main/java/com/example/stationinspector/camera/CameraXController.kt)).
- **НЕ** використовуються `ImageAnalysis`, детекція облич/сцен, ані CameraX Extensions. Не додавати без явної потреби — пайплайн навмисно мінімальний.
- `ImageCapture` налаштовано `FLASH_MODE_AUTO` + `CAPTURE_MODE_MINIMIZE_LATENCY`.

## ВИРІШЕНО — витоки пам'яті/сховища
- **Bitmap-витоки виправлено** — `originalBitmap.recycle()` після компресії у [CameraXController.kt:111](../app/src/main/java/com/example/stationinspector/camera/CameraXController.kt); `bitmap.recycle()` у [ZoneInspectionViewModel.kt:139](../app/src/main/java/com/example/stationinspector/ui/inspection/ZoneInspectionViewModel.kt).
- **`stopCamera()` повністю звільняє камеру** ([CameraXController.kt:139](../app/src/main/java/com/example/stationinspector/camera/CameraXController.kt)) — unbind use-cases, ресурси не «течуть» при виході з екрана.
- **Витік сховища фото виправлено** — `FileStorageManager.clearAllPhotoFiles()` тепер викликається при очистці даних (`StationRepositoryImpl.clearAllData()`); раніше видалялися лише рядки БД, файли лишалися на диску. Усі шляхи фото централізовано у `FileStorageManager`.

## Пайплайн (короткий)
1. `CameraXController.takePicture()` → callback з `ImageProxy` → bitmap.
2. `ImageCompressor` → resize + quality, `originalBitmap.recycle()`.
3. `FileStorageManager.savePhoto(...)` → постійний файл у `filesDir`.
4. `PhotoDao.insert(PhotoEntity(...))` — рядок у БД.
5. UI оновлюється через `Flow` від `PhotoDao`.

## 3 зони + 2 категорії
- Зони: enum **`PhotoZone`** = `ENTRANCE` / `PLATFORM` / `RESTROOM` ([domain/model/Enums.kt](../app/src/main/java/com/example/stationinspector/domain/model/Enums.kt)); чеські лейбли в UI — Nádraží / Čekárna / WC.
- Категорії: **`PhotoCategory`** — normal vs defect. Окремі лічильники в UI (проекція `StationWithSplitCounts`).

## Залишок / що покращити
- Magic-числа компресії (target width, quality) можна винести в константи `ImageCompressor`.
- EXIF orientation — переконатися, що зберігається після ресайзу.
- `PERMISSION_DENIED` — фолбек UI замість мовчазного провалу.

## Експорт залежить від цього шару
ZIP-структура (див. [06-export-background.md](06-export-background.md)) будується з `PhotoEntity.filePath` + `PhotoZone`. Перейменування / реструктуризація сховища = ламає експорт.

## Готово, коли
- Усі шляхи фото централізовано у `FileStorageManager` (виконано).
- Видалення/очистка чистить файли, а не лише рядки (виконано — `clearAllData()` → `clearAllPhotoFiles()`).
- Bitmap-и не течуть (виконано — `recycle()` у контролері та VM).
