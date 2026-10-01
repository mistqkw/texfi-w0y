#!/usr/bin/env bash
# Собирает w0y для Linux и ставит для текущего пользователя:
#   ~/.local/share/w0y-app, команда `w0y`, ярлык в меню приложений.
# Нужны JDK 21 (JAVA_HOME) и mpv (звук играет он).
set -euo pipefail
cd "$(dirname "$0")"
./gradlew createDistributable
D="$HOME/.local/share/w0y-app"
rm -rf "$D"
mkdir -p "$D" "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/192x192/apps"
cp -r build/compose/binaries/main/app/w0y/. "$D/"
ln -sf "$D/bin/w0y" "$HOME/.local/bin/w0y"
cp src/main/resources/icon.png "$HOME/.local/share/icons/hicolor/192x192/apps/w0y.png"
cat > "$HOME/.local/share/applications/w0y.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=w0y
GenericName=YouTube Music
Comment=TexFi w0y — клиент YouTube Music
Exec=$D/bin/w0y
Icon=w0y
Terminal=false
Categories=AudioVideo;Audio;Player;
StartupWMClass=com-texfi-w0y-desktop-MainKt
DESKTOP
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
command -v mpv >/dev/null || echo "Внимание: mpv не найден — поставь его (sudo pacman -S mpv)."
echo "Готово: запускай командой w0y или из меню приложений."
