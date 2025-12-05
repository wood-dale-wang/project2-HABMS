# Integration test script for HABMS
# Steps: copy deps, build, start server, generate sample.xlsx, import, export, generate PDF, stop server

Set-StrictMode -Version Latest
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
# project root is parent of tools folder
$Root = Resolve-Path (Join-Path $ScriptDir "..")
Push-Location $Root

Write-Output "Copying dependencies and building..."
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
mvn -DskipTests package

Write-Output "Starting server..."
$cp = "target/classes;target/dependency/*"
$arg = "-cp `"$cp`" com.habms.server.Server"
$proc = Start-Process -FilePath java -ArgumentList $arg -RedirectStandardOutput server.log -RedirectStandardError server.err -PassThru
Write-Output "Server PID: $($proc.Id)"

# wait for server port 9090 to be open
$max = 30; $i=0
while ($i -lt $max) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient('127.0.0.1',9090);
        $c.Close(); break
    } catch { Start-Sleep -Seconds 1; $i++ }
}
if ($i -ge $max) { Write-Error "Server did not start within timeout"; exit 1 }
Write-Output "Server is up"

# generate sample excel
Write-Output "Generating sample.xlsx via SampleDataGenerator"
java -cp "target/classes;target/dependency/*" com.habms.tools.SampleDataGenerator sample.xlsx
if (-not (Test-Path sample.xlsx)) { Write-Error "sample.xlsx not created"; exit 1 }

# helper to send JSON and receive one-line response
function Send-JsonLine($json) {
    $tcp = New-Object System.Net.Sockets.TcpClient('127.0.0.1',9090)
    $stream = $tcp.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream); $writer.AutoFlush = $true
    $writer.WriteLine($json)
    $reader = New-Object System.IO.StreamReader($stream)
    $line = $reader.ReadLine()
    $tcp.Close()
    return $line
}

# login as admin (sessionless login sets server-side session in that connection; our server stores session per connection, so subsequent new TCP doesn't carry session)
# To work around, the server stores session on connection; we need to perform import/export with the same connection.

# Implement a single-connection interaction for import/export/report
$tcp = New-Object System.Net.Sockets.TcpClient('127.0.0.1',9090)
$stream = $tcp.GetStream()
$writer = New-Object System.IO.StreamWriter($stream); $writer.AutoFlush = $true
$reader = New-Object System.IO.StreamReader($stream)

# login
$loginReq = '{"action":"login","username":"admin","password":"admin"}'
$writer.WriteLine($loginReq)
$resp = $reader.ReadLine(); Write-Output "LOGIN RESP: $resp"

# import sample.xlsx
$bytes = [System.IO.File]::ReadAllBytes("sample.xlsx")
$b64 = [Convert]::ToBase64String($bytes)
$impReq = '{"action":"import_doctors_xls","content":"' + $b64 + '"}'
$writer.WriteLine($impReq)
$resp = $reader.ReadLine(); Write-Output "IMPORT RESP: $resp"

# export appointments
$writer.WriteLine('{"action":"export_appointments_xls"}')
$resp = $reader.ReadLine(); Write-Output "EXPORT RESP (truncated): " + $resp.Substring(0,[Math]::Min(200,$resp.Length))
# parse JSON to get content and filename
$exp = $null
try { $exp = ConvertFrom-Json $resp } catch { Write-Error "Failed to parse export response" }
if ($exp -and $exp.status -eq 'OK') {
    $content = $exp.content
    $fname = $exp.filename
    [System.IO.File]::WriteAllBytes($fname, [Convert]::FromBase64String($content))
    Write-Output "Saved export to $fname"
} else { Write-Error "Export failed: $resp" }

# generate PDF
$writer.WriteLine('{"action":"generate_report_pdf"}')
$resp = $reader.ReadLine(); Write-Output "PDF RESP (truncated): " + $resp.Substring(0,[Math]::Min(200,$resp.Length))
$pr = $null
try { $pr = ConvertFrom-Json $resp } catch { Write-Error "Failed to parse pdf response" }
if ($pr -and $pr.status -eq 'OK') {
    $content = $pr.content
    $fname = $pr.filename
    [System.IO.File]::WriteAllBytes($fname, [Convert]::FromBase64String($content))
    Write-Output "Saved PDF to $fname"
} else { Write-Error "PDF generation failed: $resp" }

# logout and close
$writer.WriteLine('{"action":"logout"}')
$reader.ReadLine() | Out-Null
$tcp.Close()

# stop server
Write-Output "Stopping server PID $($proc.Id)"
Stop-Process -Id $proc.Id -Force
Write-Output "Done"

Pop-Location
