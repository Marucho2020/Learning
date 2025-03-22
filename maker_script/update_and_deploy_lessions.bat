@echo off
cd /d D:\Dev\Learning-all-git-page\maker_script\transform_script

echo 🔹 Running each Java file...
for %%F in (*.java) do (
    echo 🔹 Run the program: %%F
    java %%F
)

echo ✅ Done! Ready to deploy in 3 seconds
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