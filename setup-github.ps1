$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

Say ""
Say "==========================================" 'Cyan'
Say "  Bell Launcher - публікація на GitHub" 'Cyan'
Say "==========================================" 'Cyan'
Say ""
Say "Папка: $root"
Say ""

# ---------- 1. Перевірка Git ----------
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Say "[X] Git не знайдено." 'Red'
    Say "    Встановіть його звідси: https://git-scm.com/download/win"
    Say "    Під час встановлення нічого міняти не треба - тисніть Next."
    Say "    Потім закрийте це вікно і запустіть setup-github.bat ще раз."
    return
}
Say "[ok] git знайдено" 'Green'

# ---------- 2. Workflow у .github\workflows ----------
$wfDir = Join-Path $root '.github\workflows'
if (-not (Test-Path -LiteralPath $wfDir)) {
    New-Item -ItemType Directory -Path $wfDir -Force | Out-Null
}
$src = Join-Path $root 'ci-android-workflow.yml'
$dst = Join-Path $wfDir 'android.yml'

if (Test-Path -LiteralPath $src) {
    Move-Item -LiteralPath $src -Destination $dst -Force
    Say "[ok] workflow перенесено у .github\workflows\android.yml" 'Green'
} elseif (Test-Path -LiteralPath $dst) {
    Say "[ok] workflow уже на місці" 'Green'
} else {
    Say "[X] Не знайдено ані ci-android-workflow.yml, ані .github\workflows\android.yml" 'Red'
    Say "    Файл workflow загубився - напишіть мені, я надішлю його знову."
    return
}

# ---------- 3. Ім'я та пошта для комітів ----------
$gitName = (git config --global user.name)  2>$null
if ([string]::IsNullOrWhiteSpace($gitName)) {
    $gitName = Read-Host "Ваше ім'я для комітів (будь-яке, напр. Bell)"
    if ([string]::IsNullOrWhiteSpace($gitName)) { $gitName = 'Bell' }
    git config --global user.name $gitName
}
$gitMail = (git config --global user.email) 2>$null
if ([string]::IsNullOrWhiteSpace($gitMail)) {
    $gitMail = Read-Host "Ваш email для комітів"
    if ([string]::IsNullOrWhiteSpace($gitMail)) { $gitMail = 'bell@example.com' }
    git config --global user.email $gitMail
}
Say "[ok] автор комітів: $gitName <$gitMail>" 'Green'

# ---------- 4. Репозиторій ----------
if (-not (Test-Path -LiteralPath (Join-Path $root '.git'))) {
    git init | Out-Null
    Say "[ok] git-репозиторій створено" 'Green'
} else {
    Say "[ok] git-репозиторій уже є" 'Green'
}

git add -A | Out-Null
git commit -m "Bell Launcher" 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Say "[ok] коміт створено" 'Green'
} else {
    Say "[i] нових змін для коміту немає"
}

git branch -M main 2>&1 | Out-Null

# ---------- 5. GitHub ----------
Say ""
Say "Тепер створіть ПОРОЖНІЙ репозиторій на GitHub:" 'Yellow'
Say "  https://github.com/new" 'Yellow'
Say "  (не ставте галочки Add README / .gitignore / license)" 'Yellow'
Say ""

$repoUrl = Read-Host "Вставте URL репозиторію (https://github.com/НІК/НАЗВА.git)"
$repoUrl = $repoUrl.Trim()

if ([string]::IsNullOrWhiteSpace($repoUrl)) {
    Say "[X] URL не введено." 'Red'
    return
}
if ($repoUrl -notmatch '^https://github\.com/.+/.+') {
    Say "[X] Це не схоже на URL репозиторію GitHub." 'Red'
    Say "    Має бути щось на кшталт https://github.com/bell/bell-launcher.git"
    return
}
if ($repoUrl -notmatch '\.git$') { $repoUrl = $repoUrl + '.git' }

git remote remove origin 2>&1 | Out-Null
git remote add origin $repoUrl

Say ""
Say "Завантажую на GitHub... (може відкритися вікно входу)" 'Cyan'
Say ""

git push -u origin main

if ($LASTEXITCODE -ne 0) {
    Say ""
    Say "[X] Не вдалося завантажити." 'Red'
    Say "    Найчастіші причини:"
    Say "      - репозиторій на GitHub НЕ порожній (є README) - створіть новий, порожній"
    Say "      - не пройшли вхід у GitHub у вікні, що відкрилося"
    Say "      - неправильний URL"
    Say ""
    Say "    Скопіюйте текст помилки вище і надішліть мені."
    return
}

$actionsUrl = ($repoUrl -replace '\.git$', '') + '/actions'

Say ""
Say "==========================================" 'Green'
Say "  Готово! Код на GitHub." 'Green'
Say "==========================================" 'Green'
Say ""
Say "Збірка APK уже запустилася. Відкрийте:" 'Yellow'
Say "  $actionsUrl" 'Yellow'
Say ""
Say "Триває 3-6 хвилин. Коли зелена галочка - зайдіть у запуск,"
Say "унизу сторінки буде артефакт 'BellLauncher-debug-apk'."
Say "Усередині - APK, який ставиться на телефон."
Say ""
Say "Якщо буде червоний хрестик - відкрийте запуск, скопіюйте"
Say "текст помилки і надішліть мені, я виправлю."
Say ""
