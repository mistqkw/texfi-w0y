#!/usr/bin/env bash
# Ставит уже собранный w0y для текущего пользователя:
#   ~/.local/share/w0y-app, команда `w0y`, ярлык в меню приложений.
# Запуск: install-prebuilt.sh [папка_с_приложением] [папка_с_иконками]
# Без аргументов ищет `w0y/` и `icons/` рядом со скриптом (так лежит в архиве релиза).
# Нужен mpv: звук играет он.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
APP="${1:-$HERE/w0y}"
ICONS="${2:-$HERE/icons}"
[ -x "$APP/bin/w0y" ] || { echo "Не нашёл $APP/bin/w0y" >&2; exit 1; }
D="$HOME/.local/share/w0y-app"
rm -rf "$D"
mkdir -p "$D" "$HOME/.local/bin" "$HOME/.local/share/applications"
cp -r "$APP/." "$D/"
ln -sf "$D/bin/w0y" "$HOME/.local/bin/w0y"
# Иконка в нескольких размерах: меню, панель задач и переключатель окон берут свой.
for size in 48 96 192 384; do
  mkdir -p "$HOME/.local/share/icons/hicolor/${size}x${size}/apps"
  cp "$ICONS/w0y-$size.png" "$HOME/.local/share/icons/hicolor/${size}x${size}/apps/w0y.png"
done
gtk-update-icon-cache -q "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
cat > "$HOME/.local/share/applications/w0y.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=w0y
GenericName=YouTube Music
Comment=w0y: клиент YouTube Music
Exec=$D/bin/w0y
Icon=w0y
Terminal=false
Categories=AudioVideo;Audio;Player;
StartupWMClass=com-texfi-w0y-desktop-MainKt
DESKTOP
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
command -v mpv >/dev/null || echo "Внимание: mpv не найден — поставь его (например, sudo pacman -S mpv или sudo apt install mpv)."
echo "Готово: запускай командой w0y или из меню приложений."
