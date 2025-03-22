@echo off
cd /d D:\Dev\Learning-all-git-page\maker_script\transform_script

echo 🔹 Đang chạy từng file Java...
for %%F in (*.java) do (
    echo 🔹 Chạy chương trình: %%F
    java %%F
)

echo ✅ Hoàn thành!
timeout /t 3 >nul


:: Chuyển về thư mục repo gốc
cd /d D:\Dev\Learning-all-git-page

:: Thêm tất cả thay đổi vào Git
echo 🔹 Đang thêm file vào Git...
git add .

:: Commit với thông điệp tự động
echo 🔹 Đang commit thay đổi...
git commit -m "🚀 Auto update at %date% %time%"

:: Push lên nhánh main
echo 🔹 Đang đẩy lên GitHub...
git push origin main

echo ✅ Đã đẩy lên GitHub thành công!
timeout /t 3 >nul
exit