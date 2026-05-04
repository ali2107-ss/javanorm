@echo off
chcp 65001 >nul

echo Starting NormaControl...

IF NOT EXIST .env (
    echo Creating .env from .env.example...
    copy .env.example .env >nul
)

echo Stopping old containers...
docker-compose down

echo Building and starting services...
docker-compose up -d --build

echo Waiting for the application to become ready...
timeout /t 90 /nobreak

echo.
echo NormaControl is running.
echo.
echo Open in browser:
echo   Application:   http://localhost:8080
echo   Swagger API:   http://localhost:8080/api/swagger-ui
echo   Grafana:       http://localhost:3000
echo   Prometheus:    http://localhost:9090
echo.
echo Default first-run admin, configurable in .env:
echo   Email:         admin@normacontrol.local
echo   Password:      Admin1234!
echo.
pause
