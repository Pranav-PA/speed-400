# Checks that the Gemini request the app builds actually works with your key.
#
#   $env:GEMINI_API_KEY="..."
#   powershell -ExecutionPolicy Bypass -File scripts\verify-gemini.ps1
#
# Sends the SAME payload shape GeminiClient.kt sends: the question text plus the tool
# schema, nothing else. Paste the output back if anything fails.

$ErrorActionPreference = 'Continue'
$Key = $env:GEMINI_API_KEY
if (-not $Key) {
    Write-Host 'Set the key first:' -ForegroundColor Red
    Write-Host '  $env:GEMINI_API_KEY="your_key"'
    Write-Host '  powershell -ExecutionPolicy Bypass -File scripts\verify-gemini.ps1'
    exit 1
}

$Base = 'https://generativelanguage.googleapis.com/v1beta'
$Headers = @{ 'x-goog-api-key' = $Key; 'Content-Type' = 'application/json' }
$pass = 0; $fail = 0

Write-Host ''
Write-Host '1. Can the key list models? (what the model picker calls)'
try {
    $models = Invoke-RestMethod -Uri "$Base/models?pageSize=200" -Headers $Headers
    Write-Host '  PASS  key accepted' -ForegroundColor Green; $pass++
    $flash = $models.models.name -replace '^models/', '' | Where-Object { $_ -like '*flash*' } | Sort-Object -Unique
    Write-Host ''
    Write-Host '  Flash-tier models your key can use — pick one of these in the app:'
    $flash | ForEach-Object { Write-Host "    $_" }
    $model = ($flash | Where-Object { $_ -notlike '*preview*' -and $_ -notlike '*lite*' } | Select-Object -First 1)
    if (-not $model) { $model = $flash | Select-Object -First 1 }
} catch {
    Write-Host "  FAIL  could not list models: $_" -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host "2. Does function calling return a tool call? (model: $model)"
Write-Host '   Asking: "how much have I spent on fuel this year?"'
$body = @'
{
 "contents":[{"role":"user","parts":[{"text":"how much have I spent on fuel this year?"}]}],
 "tools":[{"functionDeclarations":[
   {"name":"sum_expenses","description":"Total money spent, optionally filtered by category and date range.","parameters":{"type":"OBJECT","properties":{"category":{"type":"STRING","description":"Spend category."},"from":{"type":"STRING","description":"Start date YYYY-MM-DD."}}}},
   {"name":"spec_lookup","description":"A specification from the owner handbook.","parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING","description":"What specification is wanted."}}}}
 ]}],
 "systemInstruction":{"parts":[{"text":"You route questions about a Triumph Speed 400 to exactly one tool. Never answer in prose and never state a figure yourself."}]},
 "generationConfig":{"temperature":0}
}
'@
try {
    $r = Invoke-RestMethod -Uri "$Base/models/$model`:generateContent" -Method Post -Headers $Headers -Body $body
    $call = $r.candidates[0].content.parts.functionCall | Where-Object { $_ }
    if ($call) {
        Write-Host "  PASS  returned a functionCall: $($call.name)" -ForegroundColor Green; $pass++
        Write-Host "        args: $($call.args | ConvertTo-Json -Compress)"
        if ($call.name -eq 'sum_expenses') { Write-Host '  PASS  chose sum_expenses (correct)' -ForegroundColor Green; $pass++ }
        else { Write-Host '  FAIL  did NOT choose sum_expenses' -ForegroundColor Red; $fail++ }
    } else {
        Write-Host '  FAIL  no functionCall — payload shape or model is wrong' -ForegroundColor Red; $fail++
        $r | ConvertTo-Json -Depth 6
    }
} catch {
    Write-Host "  FAIL  request failed: $_" -ForegroundColor Red; $fail++
}

Write-Host ''
Write-Host '-------------------------------------------'
Write-Host "  $pass passed, $fail failed"
if ($fail -eq 0) { Write-Host '  All good. Put the key and model into the app''s Ask tab.' }
else { Write-Host '  Paste this output back and I''ll fix it.' }
Write-Host ''
Write-Host '  NOTE: your key was used only against Google here. This script writes it nowhere.'
