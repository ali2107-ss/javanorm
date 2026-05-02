@echo off
echo Запуск НормаКонтроль...

IF NOT EXIST .env (
    echo Создаю .env файл...
    copy .env.example .env
)

echo Останавливаю старые контейнеры...
docker-compose down

echo Запускаю все сервисы...
docker-compose up -d --build

echo Жду запуска приложения...
timeout /t 90 /nobreak

echo.
echo ✅ НормаКонтроль запущен!
echo.
echo Открой в браузере:
echo   Приложение:  http://localhost:8080
echo   Swagger API: http://localhost:8080/api/swagger-ui
echo   Мониторинг:  http://localhost:3000
echo.
pause