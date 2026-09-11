param([string]$Source = "")
$ErrorActionPreference = 'Stop'
$expected = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
$folder = Join-Path $PSScriptRoot 'gradle\wrapper'
$target = Join-Path $folder 'gradle-wrapper.jar'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
function Test-Wrapper([string]$File) {
    return (Test-Path -LiteralPath $File) -and ((Get-FileHash -Algorithm SHA256 -LiteralPath $File).Hash.ToLowerInvariant() -eq $expected)
}
if (Test-Wrapper $target) { Write-Host 'Official Gradle 8.11.1 wrapper verified.'; exit 0 }
$temp = Join-Path $folder ('wrapper-' + [Guid]::NewGuid().ToString('N') + '.tmp')
try {
    if ($Source) {
        Copy-Item -LiteralPath $Source -Destination $temp
    } else {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $urls = @(
            'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar',
            'https://github.com/gradle/gradle/raw/refs/tags/v8.11.1/gradle/wrapper/gradle-wrapper.jar'
        )
        foreach ($url in $urls) {
            try {
                Write-Host "Downloading official Gradle wrapper: $url"
                Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $temp -TimeoutSec 60
                if (Test-Wrapper $temp) { break }
                Write-Warning 'Downloaded file did not match the official checksum.'
            } catch { Write-Warning $_.Exception.Message }
        }
    }
    if (-not (Test-Wrapper $temp)) {
        throw 'Wrapper download or SHA-256 verification failed. See README.md. Do not disable checksum verification.'
    }
    Move-Item -LiteralPath $temp -Destination $target -Force
    Write-Host 'Official Gradle 8.11.1 wrapper installed and verified.'
} finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force } }
