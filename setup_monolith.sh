#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
#  MONOLITH OS v1500 - TERMUX HYBRID SYSTEM INSTALLATION AND LAUNCHER SCRIPT
# ==============================================================================
echo "🛰️  [MONOLITH OS] Инициализация процесса установки..."

# 1. Обновление пакетного репозитория
echo "🧹 [1/6] Синхронизация репозиториев Termux APTS..."
pkg update -y && pkg upgrade -y

# 2. Установка системных зависимостей
echo "📦 [2/6] Установка пакетов компиляции (Python, OpenCV, Tools)..."
pkg install -y git python clang fftw make libjpeg-turbo libpng ndk-sysroot opencv numpy flask termux-api termux-tools termux-exec

# 3. Обновление Python пипа и зависимостей
echo "🐍 [3/6] Настройка окружения Python PIP..."
python -m pip install --upgrade pip
pip install numpy flask opencv-python werkzeug

# 4. Проверка и развертывание файлов репозитория
echo "📁 [4/6] Развертывание кодовой базы Monolith..."
MONOLITH_DIR="$HOME/monolith"

if [ -d "$MONOLITH_DIR" ]; then
    echo "🔄 Каталог найден. Синхронизируем код..."
    cd "$MONOLITH_DIR"
    git pull origin main || true
else
    echo "📥 Клонирование репозитория в локальную директорию..."
    git clone https://github.com/UFOdom/monolith.git "$MONOLITH_DIR" || {
        echo "⚠️ Примечание: Не удалось загрузить из Git. Создаем локальные файлы..."
        mkdir -p "$MONOLITH_DIR/scans"
        mkdir -p "$MONOLITH_DIR/static"
        # Будет скопировано из текущего каталога, если скрипт запущен локально
    }
    cd "$MONOLITH_DIR"
fi

# 5. Интеграция Termux:Widget ярлыка запуска на рабочий стол
echo "📱 [5/6] Создание ярлыков Termux:Widget..."
mkdir -p "$HOME/.shortcuts"
SHORT_PATH="$HOME/.shortcuts/monolith.sh"

cat << 'EOF' > "$SHORT_PATH"
#!/data/data/com.termux/files/usr/bin/bash
echo "🚀 Запуск Monolith OS v1500 Сервера..."
cd $HOME/monolith
python server.py > server.log 2>&1 &
sleep 2.5
termux-open-url http://localhost:5002
echo "✅ Монитор Monolith OS успешно активирован."
EOF

chmod +x "$SHORT_PATH"

# 6. Запуск локального сервера
echo "🔥 [6/6] Инициализация локального ядра VisionSystem на порту 5002..."
python server.py > server.log 2>&1 &
sleep 3

# Открытие PWA в полноэкранном режиме
termux-open-url http://localhost:5002

echo ""
echo "=========================================================================="
echo "✅ [SUCCESS] Установка Monolith OS v1500 завершена."
echo "🌍 Веб-пульт запущен на: http://localhost:5002"
echo "🌟 Для запуска в Kiosk режиме (PWA - без адресной строки):"
echo "   1. Откройте в Chrome и нажмите 'Добавить на главный экран'."
echo "   2. Запустите созданную иконку с рабочего стола."
echo "📱 Termux:Widget ярлык настроен: вы можете вынести виджет Termux на экран"
echo "   и запускать локальное CV-ядро одной кнопкой!"
echo "=========================================================================="
