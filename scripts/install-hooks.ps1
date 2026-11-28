# Installs Spendwise pre-commit secret scanner into .git/hooks/pre-commit
$hookPath = Join-Path $PSScriptRoot "..\.git\hooks\pre-commit"
Copy-Item -Path (Join-Path $PSScriptRoot "pre-commit.sh") -Destination $hookPath -Force
Write-Host "Installed secret pre-commit hook to $hookPath"
