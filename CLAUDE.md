# Artefact Launcher

Android-лаунчер у стилі Niagara Launcher. Kotlin + Jetpack Compose.
**Мова спілкування й коментарів у коді — українська.**

Package лишається `com.bell.launcher`, хоча застосунок називається Artefact Launcher.
Змінювати package НЕ можна — це зробить оновлення новим застосунком і зітре налаштування.

---

## Команди

```bash
# збірка debug APK
gradlew.bat assembleDebug          # Windows
./gradlew assembleDebug            # Linux/macOS
# APK: app/build/outputs/apk/debug/app-debug.apk

# швидка перевірка компіляції без пакування (у рази швидше)
gradlew.bat compileDebugKotlin

# встановити на під'єднаний телефон
gradlew.bat installDebug

# логи лаунчера
adb logcat -s ArtefactLauncher WeatherRepository AndroidRuntime
```

Потрібні JDK 17 і Android SDK (compileSdk 35). Якщо `gradlew` не знаходить SDK —
створити `local.properties` з рядком `sdk.dir=C\:\\Users\\<ім'я>\\AppData\\Local\\Android\\Sdk`.

**Перед тим як казати «готово», запусти `compileDebugKotlin`.** Компілятор ловить
90% помилок, які тут коштували цілих ітерацій наосліп.

### Підпис і встановлення на телефон

Play Захист блокує APK, підписані **debug-ключем** — для нього це застосунок
від невідомого розробника. Тому для телефона збираємо release зі своїм ключем:

```bash
MAKE-KEY.bat                       # один раз: створює artefact-release.jks
# скопіювати keystore.properties.example → keystore.properties, вписати пароль
gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

`keystore.properties` і `*.jks` у `.gitignore`. **Ключ і пароль треба зберегти**:
без них наступна версія не встановиться поверх старої, тільки поруч із нуля.

Друга причина блокування — `QUERY_ALL_PACKAGES`. Його прибрано з маніфеста
(див. коментар там) і **повертати не можна**: лаунчеру він не потрібен,
а Google вважає його особливо чутливим.

Debug-збірка має `applicationIdSuffix = ".debug"`, тобто це окремий застосунок.
Перед першим встановленням release стару debug-версію треба видалити.

### Публікація
`PUSH.bat` — комітить усе й пушить у `main`, після чого GitHub Actions збирає APK
і ганяє smoke-тест на емуляторі (API 34 і 36). Репозиторій:
https://github.com/BellCrunel/Android-them-artefact

---

## Архітектура

```
LauncherApp            → AppContainer: усі репозиторії, живе весь час роботи
MainActivity           → HOME-активність, тримає LauncherTheme + LauncherRoot
LauncherViewModel      → єдиний StateFlow<LauncherUiState> (combine 4 потоків)
LauncherRoot           → фон + HomeScreen + оверлеї (Overlay enum)

data/
  AppRepository        LauncherApps: список встановлених додатків
  LayoutRepository     розкладка головного екрана (JSON, sealed HomeEntry)
  SettingsRepository   SharedPreferences → StateFlow<LauncherSettings>
  WeatherRepository    Open-Meteo + геолокація + фолбек на часовий пояс
  NotificationStore    singleton — служба сповіщень не має доступу до AppContainer
  WallpaperRepository  вбудовані (assets) + додані користувачем (filesDir)
  UsageCounter         частота запусків із загасанням, для сортування

theme/
  ThemeManifest        модель manifest.json теми (kotlinx.serialization)
  ThemeRepository      вбудовані теми з assets + імпорт .ltheme (zip)
  Appearance           ЗВОДИТЬ тему і налаштування: налаштування завжди сильніші
  IconLoader           кеш іконок (LruCache 700), 4 джерела по черзі
  IconRenderer         форма / підкладка / масштаб / тінт
  IconStyleSampler     зразки стилів для екрана «Вигляд», в обхід IconLoader
  LauncherTheme        MaterialTheme + CompositionLocals

ui/home/
  HomeScreen           Crossfade: список обраних ⇄ список однієї літери
  LetterRail           алфавітна смуга (ліва або права), fisheye, клац+вібрація
  ClockHeader          великі цифри + рядок «дата · погода»
ui/settings/
  AppearanceScreen     ОДИН екран: Тема · Фон · Іконки · Текст + передперегляд
  SettingsScreen       решта налаштувань
ui/gestures/
  LauncherGestures     спостерігач свайпів, що НЕ споживає події
```

---

## Правила, які тут вистраждані

Кожне з них — наслідок реального бага. Не порушувати.

1. **Нічого, що читає Context, в ініціалізаторах полів Activity.**
   `WidgetController(this)` як поле → виконувався в конструкторі до
   `attachBaseContext()` → `AppWidgetHost` смикав `context.getMainLooper()` на null →
   NPE, активність не створювалась, система крутила HOME по колу (чорний екран,
   телефон гальмує). Тільки `lateinit` + створення в `onCreate`.

2. **`Text` без явного кольору бере `LocalContentColor`, а він ЧОРНИЙ**, поки екран
   не всередині `Surface`. Фон через `Modifier.background` не рахується.
   `LauncherTheme` провайдить `LocalContentColor`, екрани налаштувань — у `Surface`.

3. **Тінт іконки накладається ЛИШЕ на шар іконки, до підкладки.** Тінт готової
   картинки з `SRC_IN` зафарбовує все непрозоре одним кольором — виходили білі плитки.

4. **Деформація літер алфавіту рахується в `graphicsLayer`, не в тілі composable.**
   Читання позиції пальця всередині `graphicsLayer` відкладає інвалідацію до фази
   малювання; інакше Compose перескладає 26 `Text` на кожен рух пальця і все лагає.

5. **`Crossfade` перемикається лише між булевими станами**, не між літерами.
   Інакше кожна нова літера під пальцем запускає повну анімацію переходу.

6. **Жести не конкурують із прокруткою.** `HomeScrollState` повідомляє назовні, чи є
   куди гортати; свайп угору відкриває пошук лише коли список у кінці.
   Плюс жест живе не на всій ширині, а в смузі `bandStart..bandEnd` — половина
   екрана, протилежна колонці з рядками, мінус край зі смугою алфавіту.

7. **Декодування зображень — не на головному потоці.** `produceState` виконується
   в Main; великий `BitmapFactory.decodeStream` там з'їдає кадри.
   Загортати у `withContext(Dispatchers.IO)`.

8. **Клац/вібрація через системний `View`**, не через власний Vibrator:
   `performHapticFeedback(CLOCK_TICK)` + `playSoundEffect(CLICK)` підкоряються
   налаштуванням телефона.

9. **Погода мусить працювати без дозволів.** Ланцюжок місця:
   геолокація → активний запит координат → координати міста → назва міста →
   **часовий пояс** («Europe/Kyiv» → Kyiv). Стан назовні — `WeatherStatus`,
   щоб у налаштуваннях була видима причина, а не глухе «Немає даних».
   Прапорець `running` знімається в `finally` — інакше скасований запит
   назавжди блокує наступні.

10. **`.bat` не може містити кирилицю** — cmd парсить його в OEM-кодуванні й
    розсинхронізовується навіть на рядках `if exist`. `.ps1` має бути UTF-8 **з BOM**.

11. **Сторона екрана питається в одному місці.** `SideLayout` у `HomeScreen`
    зводить докупи дзеркало: сторону смуги, вирівнювання рядків і годинника,
    бік планки, бік показника літери. Нічого з цього не рахувати на місці —
    інакше при наступній правці одне оновлять, а друге забудуть.

12. **Прибитий годинник — окремий шар.** Він більше не елемент списку:
    лежить у тому ж `Box` ПІСЛЯ нього, а список отримує верхній відступ по
    **виміряній** висоті заголовка (масштаб шрифта в системі зсуває її на
    десятки пікселів). Рядки під ним розчиняються маскою `DstIn` — тільки
    з `CompositingStrategy.Offscreen`, інакше маска з'їсть і шпалери.


---

## Стиль коду

- Коментарі українською, пояснюють **чому**, а не що. Якщо рядок виглядає дивно —
  поруч має бути причина, інакше наступна ітерація його «полагодить» назад у баг.
- KDoc над кожним публічним composable і репозиторієм — одне-два речення.
- Ширина рядка ~100 символів.
- `runCatching` на всьому, що може впасти на чужій прошивці: лаунчеру важливіше
  запуститися, ніж бути правим.
- Нові налаштування: поле в `LauncherSettings` + ключ + читання + запис + сеттер
  (усе в `SettingsRepository`), далі метод у `LauncherViewModel`, далі UI.

## Формат тем

`docs/THEME_FORMAT.md` — повний опис `manifest.json` (v2).
Вбудовані теми: `app/src/main/assets/themes/{aurora,mono,neon}/`.
У всіх трьох `icons.background = null` — користувач просив прибрати підкладку.
Mono Paper свідомо лишає `tint` (світла тема з темними іконками).

## Діагностика на телефоні

`CrashLog` пише крихти шляху запуску й необроблені винятки у файл. Якщо при старті
знайдено звіт — замість лаунчера показується `CrashScreen` із кнопкою «скопіювати».
Не прибирати: це єдиний спосіб дізнатися, чому лаунчер не піднявся на чужому телефоні.

## Черга завдань

`docs/PLAN.md` — актуальний план із деталями реалізації й описом пасток.
Зазирати туди перед тим, як братися за нову задачу.

## Що з Niagara ще не зроблено

Медіа-віджет, календар, попередній перегляд тексту сповіщення під назвою,
подвійний тап по алфавіту = блокування екрана.

## Стан файлів, які легко переплутати

- `ui/components/AlphabetScrubber.kt` — **живий**, але це ІНШИЙ покажчик: ним
  користуються шухляда (`AppDrawer`) і `FavoritesScreen`. Головний екран має свій
  `ui/home/LetterRail.kt` із fisheye-деформацією. Не зливати їх, поведінка різна.
- `ui/folder/FolderSheet.kt` — з нього використовується лише `RenameDialog`
  (у `LauncherRoot`); сам аркуш папки більше нікуди не підключений.
- `ui/home/DragController.kt` — назва бреше: перетягування там немає, зате є
  `HomeRowAction` (пункти контекстного меню рядка) і `rememberExpandedFolders`.
  **Живий і потрібний.** Видаляти не можна, хоч ім'я й просить.
