@echo off
cd /d D:\Dev\Learning-all-git-page\maker_script\transform_script

echo 🔹 Running each Java file...
set "error_found=0"

for %%F in (*.java) do (
    echo 🔹 Running: %%F
    java %%F
    if %ERRORLEVEL% NEQ 0 (
        echo ❌ ERROR: %%F failed to execute!
        set "error_found=1"
    )
)

if %error_found% NEQ 0 (
    echo ❌ Errors detected. Deployment aborted!
    exit /b
)

echo ✅ All Java files executed successfully! Proceeding with deployment...
timeout /t 3 >nul

:: Chuyển về thư mục repo gốc
cd /d D:\Dev\Learning-all-git-page

:: Thêm tất cả thay đổi vào Git
echo 🔹 Adding files to Git...
git add .

:: Commit với thông điệp tự động
echo 🔹 Committing changes...
git commit -m "🚀 Auto update at %date% %time%"

:: Push lên nhánh main
echo 🔹 Pushing to GitHub...
git push origin main

echo ✅ Pushed to GitHub successfully!
timeout /t 3 >nul
exit
