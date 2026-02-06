param(
    [string]$url,
    [string]$outputFile
)

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Write-Host "Downloading Gradle from $url to $outputFile"
    Invoke-WebRequest -Uri $url -OutFile $outputFile
} catch {
    Write-Error "Failed to download Gradle: $_"
    exit 1
}
