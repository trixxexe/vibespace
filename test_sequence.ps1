$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

function Tap($x, $y, $delay=1) {
    & $adb shell input tap $x $y
    Start-Sleep -Seconds $delay
}

function Key($code, $delay=1) {
    & $adb shell input keyevent $code
    Start-Sleep -Seconds $delay
}

Write-Host "=== Starting Phase 1: Editor Toolbar Buttons ==="
# Save
Tap 60 360 1
# Undo
Tap 170 360 1
# Redo
Tap 280 360 1
# Format
Tap 390 360 1
# Search in File
Tap 500 360 1
# Type in search
& $adb shell input text "print"
Start-Sleep -Seconds 1
# Close search
Key 4 1
# AI inline
Tap 700 360 1
Key 4 1
# Terminal toggle
Tap 820 360 1
Tap 820 360 1
# Run Play green triangle
Tap 930 360 2

Write-Host "=== Starting Phase 2: Accessory Bar & Virtual Keyboard ==="
# Templates button
Tap 100 1770 1
# Click first template if open
Tap 300 2000 1
Key 4 1
# Snippets
Tap 350 1770 1
Tap 750 1770 1
# Virtual keys
Tap 450 1870 1 # TAB
Tap 570 1870 1 # {
Tap 670 1870 1 # }
Tap 770 1870 1 # (
Tap 870 1870 1 # )
Tap 970 1870 1 # [
Tap 60 1870 1  # undo
Tap 150 1870 1 # redo
Tap 250 1870 1 # left
Tap 340 1870 1 # right

Write-Host "=== Starting Phase 3: Bottom Terminal Panel Tabs & Runners ==="
# Open terminal
Tap 820 360 1
# Output tab
Tap 240 1700 1
# Problems tab
Tap 400 1700 1
# Console tab
Tap 580 1700 1
# Terminal tab
Tap 90 1700 1
# Runner dropdown
Tap 750 1700 1
Tap 750 1780 1 # select item
# Add terminal session
Tap 950 1770 1
# Switch terminal tabs
Tap 100 1770 1
Tap 300 1770 1
# Close panel
Tap 950 1700 1

Write-Host "=== Starting Phase 4: Top Navigation Buttons ==="
# Explorer drawer
Tap 170 130 1
# Files tab
Tap 80 220 1
# Git tab
Tap 220 220 1
# Files tab
Tap 80 220 1
# Close drawer
Tap 800 1000 1
# Command palette
Tap 610 130 1
& $adb shell input text "test"
Start-Sleep -Seconds 1
Key 4 1
# AI Assistant
Tap 720 130 1
Key 4 1
# Extensions
Tap 830 130 2
Tap 200 230 1 # Installed tab
Tap 350 230 1 # Themes tab
Tap 500 230 1 # Languages tab
Tap 80 230 1  # All tab
Key 4 2       # back
# Settings
Tap 940 130 2
& $adb shell input swipe 540 1500 540 500 300
Start-Sleep -Seconds 1
Key 4 2       # back
# Home
Tap 60 130 2
# Back to demo project
Tap 270 800 2

Write-Host "=== Test sequence finished. Checking for crashes ==="
