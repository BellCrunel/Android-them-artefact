# Приклад теми «Sunset»

Шаблон, з якого зручно почати власну тему. Формат — v2 (список + заголовок-годинник).

## Як зібрати

```bash
cd sample-theme
zip -r ../Sunset.ltheme .
```

У Windows: виділити **вміст** папки (не саму папку) → «Надіслати → Стиснута ZIP-папка» →
за бажанням перейменувати `.zip` на `.ltheme` (лаунчер приймає обидва розширення).

## Що спробувати змінити першим

| Хочу | Правити |
|---|---|
| Інший вигляд цифр | `header.clockSizeSp`, `header.clockWeight`, `header.clockLetterSpacingEm` |
| `10:41` замість `1041` | `header.clockSeparator: ":"` |
| Заголовок по центру | `header.align: "center"` і `layout.align: "center"` |
| Прибрати погоду | `header.showWeather: false` |
| Монохромні іконки | `icons.tint: "#FFFFFF"`, `icons.shape: "original"` |
| Плитки під іконками | `icons.shape: "rounded"`, `icons.scale: 0.6`, `icons.background: "#1E1E28"` |
| Щільніший список | `layout.rowSpacingDp: 6`, `layout.iconSizeDp: 34` |
| Текст без іконок | `layout.showIcons: false` |

## Як додати власні іконки

Створіть підпапку `icons/` і покладіть туди PNG 192×192 з прозорим фоном,
назвавши файли за package name додатка:

```
icons/
├── com.android.chrome.png
├── com.google.android.youtube.png
└── com.spotify.music.png
```

Package name видно в URL Play Market: `https://play.google.com/store/apps/details?id=<package>`.

## Як додати шпалери й шрифти

- `wallpaper.jpg` у корінь → `"wallpaper": { "image": "wallpaper.jpg" }`
- `fonts/Body.ttf` → `"typography": { "fontFamily": "fonts/Body.ttf" }`
- `fonts/Clock.ttf` → `"header": { "clockFont": "fonts/Clock.ttf" }`

Повний опис усіх полів — у `docs/THEME_FORMAT.md`.
