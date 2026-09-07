$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

function Say($text, $color = 'Gray') { Write-Host $text -ForegroundColor $color }

Say ""
Say "==========================================" 'Cyan'
Say "  Bell Launcher - залити зміни й зібрати" 'Cyan'
Say "==========================================" 'Cyan'
Say ""

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Say "[X] Git не знайдено." 'Red'
    return
}
if (-not (Test-Path -LiteralPath (Join-Path $root '.git'))) {
    Say "[X] Тут ще немає git-репозиторію." 'Red'
    Say "    Спершу запустіть setup-github.bat"
    return
}

$remote = git remote get-url origin 2>$null
if ([string]::IsNullOrWhiteSpace($remote)) {
    Say "[X] Не налаштовано origin. Запустіть setup-github.bat" 'Red'
    return
}
Say "Репозиторій: $remote"
Say ""

git add -A
$changes = git status --porcelain
if ([string]::IsNullOrWhiteSpace($changes)) {
    Say "[i] Змін немає - нічого заливати." 'Yellow'
    Say ""
    Say "Якщо хочете просто перезапустити збірку - відкрийте Actions,"
    Say "оберіть 'Build APK' і натисніть 'Run workflow'."
    return
}

Say "Змінені файли:"
git status --short
Say ""

$msg = Read-Host "Опис змін (Enter - залишити 'update')"
if ([string]::IsNullOrWhiteSpace($msg)) { $msg = 'update' }

git commit -m $msg | Out-Null
if ($LASTEXITCODE -ne 0) {
    Say "[X] Не вдалося створити коміт." 'Red'
    return
}
Say "[ok] коміт створено" 'Green'

Say ""
Say "Завантажую на GitHub..." 'Cyan'
git push origin main

if ($LASTEXITCODE -ne 0) {
    Say ""
    Say "[X] Не вдалося завантажити. Скопіюйте помилку вище і надішліть мені." 'Red'
    return
}

$actionsUrl = ($remote -replace '\.git$', '') + '/actions'
Say ""
Say "[ok] Готово. Нова збірка вже запустилася:" 'Green'
Say "  $actionsUrl" 'Yellow'
Say ""
Say "Через 4-5 хвилин у завершеному запуску внизу буде"
Say "оновлений артефакт 'BellLauncher-debug-apk'."
Say ""
