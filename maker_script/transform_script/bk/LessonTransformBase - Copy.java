import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class LessonTransformBase {

    private static String filePath;
    private static String outputPath;
    private static String lessonDir;
    private static String baseGitHubUrl;
    private static String currentListFileName;

    public static void main(String[] args) {
        String configPath = "config.json";
        File cFile = new File(configPath);
        System.out.println("📂 Working Directory hiện tại: " + System.getProperty("user.dir"));
        System.out.println("📂 Kiểm tra file config tại: " + cFile.getAbsolutePath());

        List<Config> configs = loadConfig(configPath);

        if (configs.isEmpty()) {
            System.err.println("❌ KHÔNG TÌM THẤY hoặc KHÔNG PARSE ĐƯỢC cấu hình nào từ " + configPath);
            return;
        }

        System.out.println("🎯 Tìm thấy " + configs.size() + " cấu hình. Bắt đầu xử lý...\n");

        for (Config config : configs) {
            try {
                System.out.println("==================================================");
                System.out.println("🔹 Đang xử lý: " + config.filePath);

                // Kiểm tra file txt nguồn có tồn tại không
                if (!Files.exists(Paths.get(config.filePath))) {
                    System.err.println("⚠️ Bỏ qua: File nguồn không tồn tại -> " + config.filePath);
                    continue;
                }

                filePath = config.filePath;
                outputPath = config.outputPath;
                lessonDir = config.lessonDir;
                baseGitHubUrl = config.baseGitHubUrl;

                // 🔹 Tạo thư mục cha cho outputPath và lessonDir nếu chưa có
                Path parentOutputDir = Paths.get(outputPath).getParent();
                if (parentOutputDir != null) {
                    Files.createDirectories(parentOutputDir);
                }

                // 🔹 Xóa file HTML cũ & thư mục lesson cũ, tạo lại
                deleteFile(outputPath);
                deleteDirectory(lessonDir);
                Files.createDirectories(Paths.get(lessonDir));

                currentListFileName = extractFileName(outputPath);

                // 🔹 Chuẩn hóa tiêu đề bài học trong file txt
                normalizeLessonTitles(filePath);

                // 🔹 Đọc và trích xuất các bài học
                List<Lesson> lessons = parseLessons(filePath, lessonDir);

                // 🔹 Tạo file html danh sách và các file bài học
                generateHtml(outputPath, lessons);

                System.out.println("✅ Hoàn tất (" + lessons.size() + " bài học): " + outputPath);
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi xử lý file " + config.filePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static String extractFileName(String pathStr) {
        return Paths.get(pathStr).getFileName().toString();
    }

    // 📌 Parse JSON linh hoạt, tự gỡ bỏ dấu `\\` kép
    private static List<Config> loadConfig(String configFilePath) {
        List<Config> configs = new ArrayList<>();
        try {
            Path path = Paths.get(configFilePath);
            if (!Files.exists(path)) {
                System.err.println("❌ Không tìm thấy file cấu hình tại: " + path.toAbsolutePath());
                return configs;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);

            // Regex linh hoạt hơn: chấp nhận khoảng trắng quanh dấu hai chấm và dấu phẩy
            Pattern pattern = Pattern.compile(
                "\\{\\s*\"filePath\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"outputPath\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"lessonDir\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"baseGitHubUrl\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
            );
            Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                // Thay thế \\ thành \ để Windows đọc đúng đường dẫn
                String fPath = matcher.group(1).replace("\\\\", "\\");
                String oPath = matcher.group(2).replace("\\\\", "\\");
                String lDir = matcher.group(3).replace("\\\\", "\\");
                String bUrl = matcher.group(4);

                configs.add(new Config(fPath, oPath, lDir, bUrl));
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file cấu hình: " + e.getMessage());
        }
        return configs;
    }

    private static void deleteFile(String pathStr) {
        try {
            Files.deleteIfExists(Paths.get(pathStr));
        } catch (IOException ignored) {}
    }

    private static void deleteDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file.getAbsolutePath());
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    // 🔹 CHUẨN HÓA TIÊU ĐỀ BÀI HỌC
    static void normalizeLessonTitles(String pathStr) throws IOException {
        Path p = Paths.get(pathStr);
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        List<String> updatedLines = new ArrayList<>();
        int lessonIndex = 1;

        for (String line : lines) {
            if (line.matches("//\\s*=+.*")) {
                String lessonName = line.replaceAll("//\\s*=+", "").trim();
                lessonName = lessonName.replaceAll("(?i)\\b(lession|lesson)\\s*\\d+\\s*==\\s*", "");
                lessonName = lessonName.replaceAll("\\s*//==\\s*", "");
                lessonName = lessonName.replaceAll("[=/]+$", "").trim();

                line = "//==========Lesson " + lessonIndex + " == " + lessonName + " ==========//";
                lessonIndex++;
            }
            updatedLines.add(line);
        }
        Files.write(p, updatedLines, StandardCharsets.UTF_8);
    }

    // 🔹 PHÂN TÍCH VÀ TÁCH BÀI HỌC
    static List<Lesson> parseLessons(String pathStr, String lDir) throws IOException {
        List<Lesson> lessons = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(pathStr), StandardCharsets.UTF_8);

        StringBuilder content = new StringBuilder();
        String title = "Untitled";
        int lessonCount = 1;

        for (String line : lines) {
            if (line.matches("//\\s*=+Lesson .*")) {
                if (content.length() > 0) {
                    lessons.add(new Lesson(title, content.toString(), lDir + "/lesson" + lessonCount + ".html", currentListFileName));
                    lessonCount++;
                    content.setLength(0);
                }
                title = line.replaceAll("//\\s*=+", "")
                            .replaceAll("(?i)\\blession\\s*\\d+\\b", "")
                            .trim();
            } else {
                content.append(line).append("\n");
            }
        }

        if (content.length() > 0) {
            lessons.add(new Lesson(title, content.toString(), lDir + "/lesson" + lessonCount + ".html", currentListFileName));
        }

        return lessons;
    }

    // 🔹 TẠO FILE HTML DANH SÁCH BÀI HỌC
    static void generateHtml(String outPath, List<Lesson> lessons) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Lesson List</title>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; transition: background 0.3s, color 0.3s; }")
            .append(".dark-mode { background-color: #121212; color: #e0e0e0; }")
            .append(".light-mode { background-color: #ffffff; color: #333333; }")
            .append("h1 { text-align: center; color: #bb86fc; }")
            .append(".container { width: 80%; margin: auto; text-align: center; }")
            .append(".home-btn { background: #ffcc00; color: #121212; border: none; padding: 10px 15px; ")
            .append("font-size: 16px; cursor: pointer; border-radius: 5px; font-weight: bold; margin-bottom: 15px; }")
            .append(".home-btn:hover { background: #ff9900; }")
            .append("button { background: #bb86fc; color: #121212; border: none; padding: 10px 15px; ")
            .append("font-size: 16px; cursor: pointer; border-radius: 5px; margin: 5px; }")
            .append("button:hover { background: #9b67e2; }")
            .append("ul { list-style: none; padding: 0; margin-left: 20px; }")
            .append("li { margin: 8px 0; text-align: left; }")
            .append("a { text-decoration: none; font-weight: bold; font-size: 18px; }")
            .append(".dark-mode a { color: #03dac6; } .light-mode a { color: #007bff; }")
            .append("</style></head><body onload='applyTheme()'>");

        html.append("<div class='container'>");
        html.append("<button class='home-btn' onclick='goHome()'>🏠 Home</button>");
        html.append("<h1>📖 Danh sách bài học</h1>");
        html.append("<button onclick='toggleTheme()'>🌙 Chuyển giao diện</button>");
        html.append("<ul>");

        for (Lesson lesson : lessons) {
            String lessonUrl = baseGitHubUrl + new File(lesson.link).getName();
            html.append("<li><a href='").append(lessonUrl).append("'>")
                .append(lesson.title.replace("==========", "-").replace("==", "--"))
                .append("</a></li>");

            // Lưu từng bài học thành file HTML riêng biệt
            Files.write(Paths.get(lesson.link), lesson.toHtml().getBytes(StandardCharsets.UTF_8));
        }

        html.append("</ul></div>");
        html.append("<script>")
            .append("function toggleTheme() {")
            .append("  let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';")
            .append("  document.body.className = mode; localStorage.setItem('theme', mode);")
            .append("}")
            .append("function applyTheme() {")
            .append("  let savedTheme = localStorage.getItem('theme') || 'dark-mode';")
            .append("  document.body.className = savedTheme;")
            .append("}")
            .append("function goHome() { window.location.href = '../../index.html'; }")
            .append("</script></body></html>");

        Files.write(Paths.get(outPath), html.toString().getBytes(StandardCharsets.UTF_8));
    }
}

class Config {
    String filePath;
    String outputPath;
    String lessonDir;
    String baseGitHubUrl;

    public Config(String filePath, String outputPath, String lessonDir, String baseGitHubUrl) {
        this.filePath = filePath;
        this.outputPath = outputPath;
        this.lessonDir = lessonDir;
        this.baseGitHubUrl = baseGitHubUrl;
    }
}

class Lesson {
    String title;
    String content;
    String link;
    String currentListFileName;

    Lesson(String title, String content, String link, String currentListFileName) {
        this.title = title;
        this.content = content;
        this.link = link;
        this.currentListFileName = currentListFileName;
    }

    String toHtml() {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>" + title + "</title>" +
            "<style>" +
            "body { font-family: Arial, sans-serif; transition: background 0.3s, color 0.3s; }" +
            ".dark-mode { background-color: #121212; color: #e0e0e0; }" +
            ".light-mode { background-color: #ffffff; color: #333333; }" +
            "h1 { text-align: center; color: #73d9f5; }" +
            "pre { padding: 15px; border-radius: 5px; " +
            "      white-space: pre-wrap; word-wrap: break-word; " +  
            "      overflow-x: auto; max-width: 100%; " +  
            "      transition: background 0.3s, color 0.3s; }" +
            ".dark-mode pre { background: #1e1e1e; color: #e0e0e0; }" +
            ".light-mode pre { background: #f5f5f5; color: #333333; }" +
            "#backTop, #backBottom { " +
            "   font-size: 1.5em; padding: 15px 30px; " +
            "   background: #bb86fc; color: white; text-decoration: none; " +
            "   border-radius: 8px; display: inline-block; text-align: center; margin: 10px 0; " +
            "}" +
            "#backTop:hover, #backBottom:hover { background: #9b67e2; }" +
            "button { font-size: 1.2em; padding: 10px 20px; " +
            "   background: #03dac6; color: #121212; border: none; " +
            "   cursor: pointer; border-radius: 5px; display: block; margin: 10px auto; }" +
            "button:hover { background: #02b8a3; }" +
            "</style></head><body onload='applyTheme(); checkPageHeight()'>" +
            "<div class='container' style='width: 90%; margin: auto;'>" +
            "<a id='backTop' href='../" + currentListFileName + "'>🔙 Quay lại danh sách</a><br>" + 
            "<h1>" + title.replace("==========", "-").replace("==", "--") + "</h1>" +
            "<pre>" + content + "</pre>" +
            "<a id='backBottom' href='../" + currentListFileName + "' style='display:none;'>🔙 Quay lại danh sách</a><br>" + 
            "<button onclick='toggleTheme()'>🌙 Chuyển giao diện</button>" +
            "</div>" +
            "<script>" +
            "function toggleTheme() {" +
            "   let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';" +
            "   document.body.className = mode; localStorage.setItem('theme', mode);" +
            "}" +
            "function applyTheme() {" +
            "   let savedTheme = localStorage.getItem('theme') || 'dark-mode';" +
            "   document.body.className = savedTheme;" +
            "}" +
            "function checkPageHeight() {" +
            "   if (document.body.scrollHeight > window.innerHeight * 1.2) {" +
            "       document.getElementById('backBottom').style.display = 'inline-block';" +
            "   }" +
            "}" +
            "</script>" +
            "</body></html>";
    }
}