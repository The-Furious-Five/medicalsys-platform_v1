<#
CRY-03: genera y verifica SHA256SUMS para los artefactos de entrega (version Windows).

Uso:
  .\scripts\checksums.ps1 generate [-Images img1,img2,...]   # crea SHA256SUMS
  .\scripts\checksums.ps1 verify                              # verifica con Get-FileHash
  .\scripts\checksums.ps1 -Compare <archivo>                   # SHA256 vs CRC32 vs MD5

Que incluye "generate" (solo si existe; si no, avisa y sigue):
  - JARs de cada microservicio:      services/*/target/*.jar
  - Imagenes Docker relevantes:      medicalsys/*:local de compose.yaml, detectadas
                                      con `docker images` si docker esta disponible;
                                      si no, pasa nombres con -Images.
                                      (van en un archivo aparte docker-images.sha256,
                                       porque Get-FileHash solo verifica archivos)
  - Export de Keycloak (realm-export.json o similar bajo infra/ o docs/): NO EXISTE
    en este repo a la fecha; si aparece luego, este script lo recoge solo.
  - PDFs de entrega: *.pdf en la raiz del repo y en docs/ (recursivo).

Test manual (criterio de aceptacion: detectar 1 byte modificado):
  .\scripts\checksums.ps1 generate
  .\scripts\checksums.ps1 verify                                # -> OK
  Add-Content -NoNewline -Value "x" docs\contracts\routes-v1.md  # modifica 1 byte
  .\scripts\checksums.ps1 verify                                # -> FAILED
  git checkout -- docs\contracts\routes-v1.md                    # revertir
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet('generate', 'verify')]
    [string]$Command,

    [string[]]$Images = @(),

    [string]$Compare
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$Out = 'SHA256SUMS'
$ImgOut = 'docker-images.sha256'

function Get-TargetFiles {
    $files = @()

    $jars = Get-ChildItem -Path 'services' -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]target[\\/]' }
    if ($jars.Count -eq 0) {
        Write-Warning "No se encontraron JARs compilados en services/*/target/*.jar (¿falta compilar?)"
    } else {
        $files += $jars.FullName
    }

    $pdfs = @(Get-ChildItem -Path '.' -Filter '*.pdf' -File -ErrorAction SilentlyContinue)
    $pdfs += @(Get-ChildItem -Path 'docs' -Recurse -Filter '*.pdf' -ErrorAction SilentlyContinue)
    if ($pdfs.Count -eq 0) {
        Write-Warning "No se encontraron PDFs de entrega en la raíz ni en docs/"
    } else {
        $files += $pdfs.FullName
    }

    $realm = @()
    foreach ($dir in @('infra', 'docs')) {
        if (Test-Path $dir) {
            $realm += Get-ChildItem -Path $dir -Recurse -Filter '*realm*export*' -ErrorAction SilentlyContinue
        }
    }
    if ($realm.Count -eq 0) {
        Write-Warning "No se encontró export de realm de Keycloak (realm-export.json) bajo infra/ ni docs/. Se omite."
    } else {
        $files += $realm.FullName
    }

    return $files
}

function Invoke-Generate {
    $files = Get-TargetFiles
    if ($files.Count -eq 0) {
        Write-Warning "No se encontró ningún archivo para sumar. No se crea $Out."
    } else {
        $lines = $files | ForEach-Object {
            $hash = (Get-FileHash -Algorithm SHA256 -Path $_).Hash.ToLower()
            $rel = Resolve-Path -Relative $_
            "$hash  $rel"
        }
        $lines | Set-Content -Encoding utf8 $Out
        Write-Host "Generado $Out con $($files.Count) archivo(s)."
    }

    $imgs = $Images
    if ($imgs.Count -eq 0 -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        try {
            $imgs = @(docker images --format '{{.Repository}}:{{.Tag}}' | Where-Object { $_ -match '^medicalsys/' })
        } catch {
            Write-Warning "No se pudo listar imágenes Docker (¿el daemon está corriendo?): $($_.Exception.Message)"
            $imgs = @()
        }
    }
    if ($imgs.Count -eq 0) {
        Write-Warning "No se encontraron imágenes Docker (docker no disponible, o ninguna imagen medicalsys/*:local construida). Pasa -Images img1,img2 para sumarlas."
    } else {
        $imgLines = @()
        foreach ($img in $imgs) {
            $id = $null
            try { $id = docker inspect --format '{{.Id}}' $img } catch { $id = $null }
            if (-not $id) {
                Write-Warning "No se pudo inspeccionar la imagen $img"
                continue
            }
            $imgLines += "$id  $img"
        }
        $imgLines | Set-Content -Encoding utf8 $ImgOut
        Write-Host "Generado $ImgOut con los IDs (sha256) de $($imgs.Count) imagen(es)."
    }
}

function Invoke-Verify {
    if (-not (Test-Path $Out)) {
        Write-Error "No existe $Out. Corre primero: .\scripts\checksums.ps1 generate"
        exit 1
    }
    $failed = $false
    foreach ($line in Get-Content $Out) {
        if ($line -notmatch '^(?<hash>[0-9a-fA-F]{64})\s+(?<path>.+)$') { continue }
        $expected = $Matches.hash.ToLower()
        $path = $Matches.path
        if (-not (Test-Path $path)) {
            Write-Host "$path`: FAILED (archivo no encontrado)" -ForegroundColor Red
            $failed = $true
            continue
        }
        $actual = (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLower()
        if ($actual -eq $expected) {
            Write-Host "$path`: OK"
        } else {
            Write-Host "$path`: FAILED (esperado $expected, actual $actual)" -ForegroundColor Red
            $failed = $true
        }
    }

    if ((Test-Path $ImgOut) -and (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "--- verificando imágenes Docker ---"
        foreach ($line in Get-Content $ImgOut) {
            if ($line -notmatch '^(?<id>\S+)\s+(?<img>.+)$') { continue }
            $expectedId = $Matches.id
            $img = $Matches.img
            $currentId = docker inspect --format '{{.Id}}' $img 2>$null
            if ($currentId -eq $expectedId) {
                Write-Host "$img`: OK"
            } else {
                $shown = if ($currentId) { $currentId } else { '<no existe>' }
                Write-Host "$img`: FAILED (esperado $expectedId, actual $shown)" -ForegroundColor Red
                $failed = $true
            }
        }
    }

    if ($failed) { exit 1 } else { exit 0 }
}

function Invoke-Compare([string]$Path) {
    if (-not (Test-Path $Path)) {
        Write-Error "Archivo no encontrado: $Path"
        exit 1
    }
    Write-Host "Archivo: $Path"
    $sha256 = (Get-FileHash -Algorithm SHA256 -Path $Path).Hash
    Write-Host "SHA256 (criptográfico, resistente a colisiones intencionales): $sha256"
    $md5 = (Get-FileHash -Algorithm MD5 -Path $Path).Hash
    Write-Host "MD5    (criptográfico pero roto, solo para detección casual):  $md5"

    # Get-FileHash no soporta CRC32 nativamente; se calcula a mano con la tabla
    # estandar CRC-32 (misma que usa Zip/gzip), sin dependencias nuevas.
    $crcTable = New-Object 'UInt32[]' 256
    for ($i = 0; $i -lt 256; $i++) {
        $c = [UInt32]$i
        for ($j = 0; $j -lt 8; $j++) {
            if (($c -band 1) -ne 0) { $c = 0xEDB88320 -bxor ($c -shr 1) }
            else { $c = $c -shr 1 }
        }
        $crcTable[$i] = $c
    }
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $Path))
    $crc = [UInt32]::MaxValue
    foreach ($b in $bytes) {
        $crc = $crcTable[($crc -bxor $b) -band 0xFF] -bxor ($crc -shr 8)
    }
    $crc = $crc -bxor [UInt32]::MaxValue
    Write-Host ("CRC32  (checksum simple, solo detecta errores aleatorios):     {0:x8}" -f $crc)
}

if ($Compare) {
    Invoke-Compare -Path $Compare
} elseif ($Command -eq 'generate') {
    Invoke-Generate
} elseif ($Command -eq 'verify') {
    Invoke-Verify
} else {
    Write-Host "Uso: .\scripts\checksums.ps1 {generate [-Images img1,img2]|verify|-Compare <archivo>}"
    exit 1
}
