@echo off
setlocal enabledelayedexpansion

:: 1. Chuyển vào thư mục chứa script Java để chạy
cd /d "D:\Dev\Backup-Daily\Learning-all-git-page\maker_script\transform_script"

:: Kiểm tra config.json
if not exist "config.json" (
    echo ❌ ERROR: Khong tim thay file config.json tai: %CD%
    pause
    exit /b
)

:: Xóa file .class cũ
del /f /q *.class 2>nul

echo 🔹 Running each Java file...
set "error_found=0"

for %%F in (*.java) do (
    echo --------------------------------------------------
    echo 🔹 Running: %%F
    java "%%F"
    if errorlevel 1 (
        echo ❌ ERROR: %%F failed to execute!
        set "error_found=1"
    )
)

if !error_found! NEQ 0 (
    echo ❌ Errors detected. Deployment aborted!
    pause
    exit /b
)

echo.
echo ✅ Java executed successfully! Proceeding with deployment...
timeout /t 2 >nul

:: 2. Chuyển về đúng thư mục gốc chứa Repo Git (có Backup-Daily)
cd /d "D:\Dev\Backup-Daily\Learning-all-git-page"

echo 📂 Dang o thu muc Repo: %CD%

:: Dùng git add -A (thay vì git add .) để quét sạch tất cả thay đổi trên toàn bộ Repo
echo 🔹 Adding all files to Git...
git add -A

:: Commit các thay đổi
echo 🔹 Committing changes...
git commit -m "🚀 Auto update lessons at %date% %time%"

:: Push lên GitHub
echo 🔹 Pushing to GitHub...
git push origin main

echo.
echo ✅ Hoan tat va da day len GitHub thanh cong!
pause