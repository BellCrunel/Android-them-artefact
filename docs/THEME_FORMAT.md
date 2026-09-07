# Формат тем Bell Launcher (v2)

Тема — це ZIP-архів із розширенням **`.ltheme`** (або `.zip`), усередині якого обов'язково
лежить `manifest.json`. Лаунчер розпаковує архів у свою внутрішню пам'ять і одразу застосовує тему.

Домашній екран — **вертикальний список**: зверху заголовок із великими цифрами часу
та рядком «дата · погода», під ним рядки «іконка → назва». Тема керує всім цим.

## Структура архіву

```
my-theme.ltheme
├── manifest.json          ← обов'язковий
├── preview.png            ← мініатюра для галереї тем (опційно)
├── wallpaper.jpg          ← фонове зображення (опційно)
├── fonts/
│   ├── Body.ttf           ← шрифт назв додатків
│   └── Clock.ttf          ← окремий шрифт для цифр годинника
└── icons/
    ├── com.android.chrome.png
    └── …                  ← файл називається як package name
```

Архів може містити один кореневий каталог — лаунчер знайде `manifest.json` і на рівень углиб.

## Мінімальна тема

```json
{ "id": "test.min", "name": "Мінімальна", "colors": { "dark": true, "primary": "#00E676" } }
```

## Повний manifest.json

```json
{
  "formatVersion": 2,
  "id": "com.bell.theme.sunset",
  "name": "Sunset",
  "author": "Bell",
  "version": "1.0.0",
  "description": "Теплий градієнт і великі цифри",
  "preview": "preview.png",

  "wallpaper": {
    "image": "wallpaper.jpg",
    "gradient": ["#2B1055", "#FF9A5A"],
    "dim": 0.1,
    "parallaxOnScroll": true
  },

  "colors": {
    "dark": true,
    "primary": "#FF7043",
    "onPrimary": "#1A0B05",
    "secondary": "#FFD180",
    "background": "#160D1F",
    "onBackground": "#F5ECFF",
    "surface": "#241634",
    "onSurface": "#F5ECFF",
    "outline": "#33FFFFFF",
    "homeLabel": "#FFFFFF",
    "drawerLabel": "#F5ECFF",
    "labelShadow": "#B3000000",
    "scrubber": "#FFFFFF"
  },

  "typography": {
    "fontFamily": "fonts/Body.ttf",
    "scale": 1.0,
    "allCaps": false
  },

  "header": {
    "style": "bigDigits",
    "clockFont": "fonts/Clock.ttf",
    "clockSizeSp": 78,
    "clockWeight": 700,
    "clockLetterSpacingEm": -0.05,
    "clockSeparator": "",
    "hour24": true,
    "dateFormat": "EEE, d MMM",
    "showDate": true,
    "showWeather": true,
    "subSeparator": " · ",
    "subColor": "#EDEDF5",
    "subSizeSp": 14,
    "align": "start",
    "paddingTopDp": 54,
    "paddingBottomDp": 28
  },

  "icons": {
    "shape": "circle",
    "cornerRadiusPercent": 28,
    "scale": 0.86,
    "background": "#FFF3E0",
    "normalizeLegacyIcons": true,
    "iconPackPackage": null,
    "folder": "icons",
    "map": { "com.android.chrome": "icons/my-chrome.png" },
    "tint": null
  },

  "layout": {
    "iconSizeDp": 44,
    "rowSpacingDp": 12,
    "iconGapDp": 20,
    "horizontalPaddingDp": 26,
    "align": "start",
    "showIcons": true,
    "labelSizeSp": 17,
    "labelWeight": 500,
    "drawerScrubber": true,
    "drawerShowIcons": true,
    "statusBarDark": false
  },

  "effects": {
    "drawerScrim": 0.88,
    "animationSpeed": 1.0,
    "parallax": 0.4
  }
}
```

## Секція `header` — заголовок із годинником

| Поле | Значення | Опис |
|---|---|---|
| `style` | `bigDigits` \| `compact` \| `none` | `bigDigits` — великі цифри + рядок під ними; `compact` — тільки один рядок «час · дата · погода»; `none` — без заголовка |
| `clockFont` | шлях | Окремий шрифт для цифр. Якщо не задано — береться `typography.fontFamily` |
| `clockSizeSp` | число | Розмір цифр |
| `clockWeight` | 100–900 | Товщина |
| `clockLetterSpacingEm` | число | Міжлітерний інтервал в `em`. Від'ємне значення стискає цифри (як `1041` на скріні) |
| `clockSeparator` | рядок | `""` → `1041`, `":"` → `10:41`, `" "` → `10 41` |
| `hour24` | bool | 24- чи 12-годинний формат |
| `dateFormat` | шаблон | Формат `java.time`, напр. `EEE, d MMM` → «Tue, 30 Dec» |
| `showDate`, `showWeather` | bool | Що показувати в нижньому рядку |
| `subSeparator` | рядок | Роздільник елементів рядка, напр. `" · "` |
| `subColor`, `subSizeSp` | колір / число | Вигляд нижнього рядка |
| `align` | `start` \| `center` \| `end` | Вирівнювання всього заголовка |
| `paddingTopDp`, `paddingBottomDp` | число | Відступи зверху й знизу |

### Шаблони дати

| Шаблон | Результат |
|---|---|
| `EEE, d MMM` | Tue, 30 Dec |
| `EEEE` | Tuesday |
| `d MMMM` | 30 грудня |
| `dd.MM` | 30.12 |

Мова береться з мови системи.

## Секція `layout` — список додатків

| Поле | Опис |
|---|---|
| `iconSizeDp` | Розмір іконки в рядку |
| `rowSpacingDp` | Відстань між рядками |
| `iconGapDp` | Проміжок між іконкою і назвою |
| `horizontalPaddingDp` | Відступи списку від країв екрана |
| `align` | `start` — іконка зліва, назва праворуч (як на скрінах); `center` — усе по центру |
| `showIcons` | Показувати іконки на головному екрані |
| `labelSizeSp`, `labelWeight` | Розмір і товщина назв |
| `drawerScrubber` | Алфавітний скрол A–Z справа в шухляді |
| `drawerShowIcons` | Іконки в шухляді (можна вимкнути для суто текстового списку) |

## Секція `icons`

| Поле | Опис |
|---|---|
| `shape` | `original`, `circle`, `squircle`, `rounded`, `square`, `teardrop`, `hexagon` |
| `cornerRadiusPercent` | Для `rounded`, `0..50` |
| `scale` | Масштаб іконки всередині форми, `0.4..1.0`. Маленьке значення + `background` дає ефект «іконка на плитці» як на першому скріні |
| `background` | Колір підкладки. `null` — без підкладки |
| `tint` | Перефарбувати всі іконки в один колір (монохромний вигляд) |
| `iconPackPackage` | Package встановленого icon pack (ADW/Nova) — лаунчер прочитає його `appfilter.xml` |
| `folder` | Папка з іконками в архіві; файл = `<package>.png` |
| `map` | Явні відповідності `package` → шлях у архіві |

Порядок пошуку іконки: `map` → `<folder>/<package>.png` → icon pack → системна іконка,
приведена до `shape` / `scale` / `background` / `tint`.

## Секція `colors`

`dark: true` вмикає темну схему Material 3, `false` — світлу. Кольори — `#RRGGBB` або `#AARRGGBB`.
Окремо задаються `homeLabel` (назви на головному), `drawerLabel` (у шухляді),
`labelShadow` (тінь під текстом, прозорий = без тіні) і `scrubber` (літери алфавітного скролу).

## Іконки — практичні поради

- Розмір файлів: **192×192** або **256×256**, PNG із прозорим фоном.
- Package name видно в URL Play Market: `.../details?id=<package>`.
- Для «плиткового» вигляду як на першому скріні: `shape: "rounded"`, `scale: 0.6`,
  `background: "#1E1E28"`, `tint: "#E6E6EE"`.
- Для контурного вигляду як на другому скріні: `shape: "original"`, `scale: 0.92`, без `background`.

## Як зібрати й встановити тему

```bash
cd my-theme
zip -r ../my-theme.ltheme .
```

У Windows: виділити **вміст** папки (не саму папку) → «Надіслати → Стиснута ZIP-папка» →
за бажанням перейменувати `.zip` на `.ltheme`.

На телефоні: **довгий тап по вільному місцю → Теми → Імпорт** і обрати файл.

## Якщо тема не з'явилася

- Перевірте JSON на зайві коми й лапки — при помилці парсингу тема просто ігнорується.
- `manifest.json` має бути в корені архіву або в одній підпапці, не глибше.
- `id` має бути унікальним: тема з таким самим `id` перезапише попередню.
