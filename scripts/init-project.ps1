$ErrorActionPreference = "Stop"

$Root = Resolve-Path "$PSScriptRoot\.."
Set-Location $Root

$directories = @(
    "backend",
    "frontend",
    "ai-service/app",
    "ai-service/tests",
    "iot-device/src",
    "iot-device/tests",
    "infrastructure/mosquitto/config",
    "infrastructure/mysql/init",
    "infrastructure/nginx",
    "data/uploads/justifications",
    "data/uploads/claims",
    "samples",
    "report/images",
    "presentation/images",
    "docs/adr"
)

foreach ($directory in $directories) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

$files = @(
    ".env.example",
    ".gitignore",
    "compose.yaml",
    "README.md",
    "ai-service/requirements.txt",
    "iot-device/requirements.txt",
    "samples/students-template.csv",
    "samples/schedule-template.csv"
)

foreach ($file in $files) {
    if (-not (Test-Path $file)) {
        New-Item -ItemType File -Path $file | Out-Null
    }
}

Write-Host "Arborescence initialisée dans $Root"
Write-Host "Étape suivante : créer compose.yaml puis initialiser Spring Boot."