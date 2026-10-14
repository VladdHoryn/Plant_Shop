# Builds the Plant Shop images straight into minikube's Docker daemon, so the cluster
# can use them without a registry (imagePullPolicy: IfNotPresent).
#
#   .\scripts\build-images.ps1                                   -> every service as 1.0.0, catalog-service also as 1.1.0
#   .\scripts\build-images.ps1 -Version 1.2.0 -Service catalog-service
param(
    [string]$Version,
    [string]$Service
)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& minikube -p minikube docker-env --shell powershell | Invoke-Expression

function Build-Image([string]$Name, [string]$Tag) {
    Write-Host ">>> plantshop/${Name}:${Tag}"
    docker build --build-arg SERVICE=$Name --build-arg APP_VERSION=$Tag -t "plantshop/${Name}:${Tag}" .
    if ($LASTEXITCODE -ne 0) { throw "docker build failed for $Name" }
}

if ($Version -and $Service) {
    Build-Image $Service $Version
    return
}

foreach ($name in 'config-service', 'discovery-service', 'api-gateway', 'catalog-service',
                  'inventory-service', 'order-service', 'payment-service') {
    Build-Image $name '1.0.0'
}
# Second version for the rolling update / rollback demo (same code, APP_VERSION=1.1.0)
Build-Image 'catalog-service' '1.1.0'

docker images 'plantshop/*'
