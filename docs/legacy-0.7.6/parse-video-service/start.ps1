$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (!(Test-Path '.venv\Scripts\python.exe')) {
        & py -3 -m venv .venv
        if ($LASTEXITCODE -ne 0) { throw 'Failed to create Python virtual environment.' }
    }
    & .\.venv\Scripts\python.exe -m pip install -r requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed; no service was started.' }
    & .\.venv\Scripts\python.exe server.py
    if ($LASTEXITCODE -ne 0) { throw 'Parser service exited with an error.' }
} finally { Pop-Location }
