# PowerShell script to build and run the Spring Boot application

# Set Java 21 home
$env:JAVA_HOME = 'C:\Users\User\AppData\Local\jdks\jdk-21.0.10'

# Load environment variables from .env file
Write-Host "Loading environment variables from .env file..." -ForegroundColor Yellow
Get-Content .env | ForEach-Object {
    if ($_ -and -not $_.StartsWith('#')) {
        $key, $value = $_.Split('=', 2)
        [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim())
    }
}

Write-Host "Environment variables loaded:" -ForegroundColor Green
Write-Host "DB_URL: $env:DB_URL" -ForegroundColor Green
Write-Host "JWT_SECRET is set: $(if ($env:JWT_SECRET) { 'Yes' } else { 'No' })" -ForegroundColor Green

# Build and run the application
Write-Host "Building and starting the Spring Boot backend application..." -ForegroundColor Cyan
.\mvnw.cmd spring-boot:run

# If the above command fails, show an error message
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to start the application" -ForegroundColor Red
    exit 1
}

Write-Host "Application started successfully!" -ForegroundColor Green
