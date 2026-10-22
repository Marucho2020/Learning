import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class LessonTransformBase {

    public static void main(String[] args) {
        String configPath = "config.json";
        System.out.println("📂 Thư mục làm việc: " + System.getProperty("user.dir"));

        List<Config> configs = loadConfig(configPath);

        if (configs.isEmpty()) {
            System.err.println("❌ KHÔNG TÌM THẤY hoặc file " + configPath + " bị rỗng / sai cú pháp!");
            return;
        }

        System.out.println("🎯 Tìm thấy " + configs.size() + " cấu hình môn học. Bắt đầu xử lý...\n");

        for (Config config : configs) {
            try {
                System.out.println("==================================================");
                System.out.println("🔹 Đang xử lý: " + config.filePath);

                if (!Files.exists(Paths.get(config.filePath))) {
                    System.err.println("⚠️ Bỏ qua (không tồn tại file nguồn): " + config.filePath);
                    continue;
                }

                // Tạo thư mục cha của output nếu chưa có
                Path parentOutputDir = Paths.get(config.outputPath).getParent();
                if (parentOutputDir != null) {
                    Files.createDirectories(parentOutputDir);
                }

                // Xóa file html cũ và tạo lại thư mục bài học
                deleteFile(config.outputPath);
                deleteDirectory(config.lessonDir);
                Files.createDirectories(Paths.get(config.lessonDir));

                String currentListFileName = Paths.get(config.outputPath).getFileName().toString();

                // 1. Chuẩn hóa tiêu đề trong file txt gốc
                normalizeLessonTitles(config.filePath);

                // 2. Tách các bài học
                List<Lesson> lessons = parseLessons(config.filePath, config.lessonDir, currentListFileName);

                // 3. Liên kết bài trước / bài tiếp theo
                for (int i = 0; i < lessons.size(); i++) {
                    Lesson current = lessons.get(i);
                    current.prevFileName = (i > 0) ? lessons.get(i - 1).fileName : null;
                    current.nextFileName = (i < lessons.size() - 1) ? lessons.get(i + 1).fileName : null;
                }

                // 4. Tạo file HTML danh sách và các file bài học
                String topicId = extractTopicId(config.outputPath);
                generateHtml(config.outputPath, lessons, topicId, config.baseGitHubUrl);

                System.out.println("✅ Đã tạo thành công " + lessons.size() + " bài học cho: " + config.outputPath);
            } catch (Exception e) {
                System.err.println("❌ Lỗi khi xử lý " + config.filePath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static String extractTopicId(String pathStr) {
        String name = Paths.get(pathStr).getFileName().toString();
        return name.replace(".html", "").replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private static List<Config> loadConfig(String configFilePath) {
        List<Config> configs = new ArrayList<>();
        try {
            Path path = Paths.get(configFilePath);
            if (!Files.exists(path)) {
                System.err.println("❌ Không tìm thấy file: " + path.toAbsolutePath());
                return configs;
            }

            String json = Files.readString(path, StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile(
                "\\{\\s*\"filePath\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"outputPath\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"lessonDir\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                + "\"baseGitHubUrl\"\\s*:\\s*\"([^\"]+)\"\\s*\\}"
            );
            Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                String fPath = matcher.group(1).replace("\\\\", "\\");
                String oPath = matcher.group(2).replace("\\\\", "\\");
                String lDir = matcher.group(3).replace("\\\\", "\\");
                String bUrl = matcher.group(4);
                configs.add(new Config(fPath, oPath, lDir, bUrl));
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc config: " + e.getMessage());
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
                    if (file.isDirectory()) deleteDirectory(file.getAbsolutePath());
                    else file.delete();
                }
            }
            dir.delete();
        }
    }

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

    static List<Lesson> parseLessons(String pathStr, String lDir, String currentListFileName) throws IOException {
        List<Lesson> lessons = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(pathStr), StandardCharsets.UTF_8);

        StringBuilder content = new StringBuilder();
        String title = "Untitled";
        int lessonCount = 1;

        for (String line : lines) {
            if (line.matches("//\\s*=+Lesson .*")) {
                if (content.length() > 0) {
                    String fName = "lesson" + lessonCount + ".html";
                    lessons.add(new Lesson(lessonCount, title, content.toString(), lDir + "/" + fName, fName, currentListFileName));
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
            String fName = "lesson" + lessonCount + ".html";
            lessons.add(new Lesson(lessonCount, title, content.toString(), lDir + "/" + fName, fName, currentListFileName));
        }

        return lessons;
    }

    static void generateHtml(String outPath, List<Lesson> lessons, String topicId, String baseGitHubUrl) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>\n");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        html.append("<title>Danh sách bài học</title><style>\n");
        html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n");
        html.append("margin: 0; padding: 20px; transition: background 0.3s, color 0.3s; }\n");
        html.append(".dark-mode { background-color: #121212; color: #e0e0e0; }\n");
        html.append(".light-mode { background-color: #f8f9fa; color: #212529; }\n");
        html.append(".container { max-width: 900px; margin: auto; }\n");
        html.append(".header-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 20px; }\n");
        html.append(".btn { text-decoration: none; padding: 10px 18px; border-radius: 8px; font-weight: bold; border: none; cursor: pointer; transition: 0.2s; }\n");
        html.append(".home-btn { background: #ffcc00; color: #121212; } .home-btn:hover { background: #e6b800; }\n");
        html.append(".theme-btn { background: #6c757d; color: white; } .theme-btn:hover { background: #5a6268; }\n");
        html.append("h1 { text-align: center; color: #3498db; margin: 15px 0; }\n");
        html.append(".progress-box { background: rgba(0,0,0,0.05); padding: 15px; border-radius: 10px; margin-bottom: 25px; text-align: center; }\n");
        html.append(".dark-mode .progress-box { background: #1e1e1e; }\n");
        html.append(".progress-bar-bg { background: #ccc; border-radius: 10px; height: 12px; width: 100%; overflow: hidden; margin-top: 8px; }\n");
        html.append(".progress-bar-fill { background: #2ecc71; height: 100%; width: 0%; transition: width 0.4s ease; }\n");
        html.append(".search-box { width: 100%; box-sizing: border-box; padding: 12px 18px; font-size: 16px; border-radius: 8px; border: 2px solid #ccc; margin-bottom: 20px; outline: none; }\n");
        html.append(".dark-mode .search-box { background: #222; border-color: #444; color: #fff; }\n");
        html.append("ul { list-style: none; padding: 0; }\n");
        html.append("li { margin: 8px 0; display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-radius: 8px; transition: 0.2s; background: rgba(0,0,0,0.02); }\n");
        html.append(".dark-mode li { background: #1c1c1c; }\n");
        html.append("li:hover { transform: translateX(5px); }\n");
        html.append("a.lesson-link { text-decoration: none; font-size: 17px; font-weight: 500; flex-grow: 1; color: #007bff; }\n");
        html.append(".dark-mode a.lesson-link { color: #58a6ff; }\n");
        html.append(".badge-done { background: #2ecc71; color: white; font-size: 12px; padding: 4px 8px; border-radius: 12px; display: none; }\n");
        html.append("</style></head><body onload='initPage()'>\n");
        html.append("<div class='container'>\n");
        html.append("<div class='header-bar'>\n");
        html.append("<a class='btn home-btn' href='../../index.html'>🏠 Về Trang Chủ</a>\n");
        html.append("<button class='btn theme-btn' onclick='toggleTheme()'>🌓 Đổi Giao Diện</button>\n");
        html.append("</div>\n");
        html.append("<h1>📖 Danh sách bài học</h1>\n");
        html.append("<div class='progress-box'>\n");
        html.append("<div><b>Tiến độ học tập:</b> <span id='progress-text'>Đang tải...</span></div>\n");
        html.append("<div class='progress-bar-bg'><div id='progress-fill' class='progress-bar-fill'></div></div>\n");
        html.append("</div>\n");
        html.append("<input type='text' id='searchInput' class='search-box' placeholder='🔍 Gõ để tìm nhanh bài học...' onkeyup='filterLessons()'>\n");
        html.append("<ul id='lessonList'>\n");

        for (Lesson lesson : lessons) {
            String lessonUrl = baseGitHubUrl + lesson.fileName;
            String cleanTitle = lesson.title.replace("==========", "-").replace("==", "--");
            String lessonKey = topicId + "_" + lesson.fileName;

            html.append("<li data-title='").append(cleanTitle.toLowerCase()).append("'>\n")
                .append("<a class='lesson-link' href='").append(lessonUrl).append("'>").append(cleanTitle).append("</a>\n")
                .append("<span class='badge-done' id='badge_").append(lessonKey).append("'>✓ Đã học</span>\n")
                .append("</li>\n");

            // Xuất ra từng file HTML con
            Files.write(Paths.get(lesson.link), lesson.toHtml(topicId).getBytes(StandardCharsets.UTF_8));
        }

        html.append("</ul></div>\n");
        html.append("<script>\n");
        html.append("const topicId = '").append(topicId).append("';\n");
        html.append("const totalLessons = ").append(lessons.size()).append(";\n");
        html.append("function updateProgress() {\n");
        html.append("  let doneCount = 0;\n");
        html.append("  document.querySelectorAll('li').forEach(li => {\n");
        html.append("    const link = li.querySelector('a');\n");
        html.append("    const fileName = link.getAttribute('href').split('/').pop();\n");
        html.append("    const key = topicId + '_' + fileName;\n");
        html.append("    const badge = document.getElementById('badge_' + key);\n");
        html.append("    if (localStorage.getItem(key) === 'done') {\n");
        html.append("      doneCount++;\n");
        html.append("      if(badge) badge.style.display = 'inline-block';\n");
        html.append("    } else {\n");
        html.append("      if(badge) badge.style.display = 'none';\n");
        html.append("    }\n");
        html.append("  });\n");
        html.append("  const percent = totalLessons > 0 ? Math.round((doneCount / totalLessons) * 100) : 0;\n");
        html.append("  document.getElementById('progress-text').innerText = doneCount + '/' + totalLessons + ' bài (' + percent + '%)';\n");
        html.append("  document.getElementById('progress-fill').style.width = percent + '%';\n");
        html.append("}\n");
        html.append("function filterLessons() {\n");
        html.append("  let input = document.getElementById('searchInput').value.toLowerCase();\n");
        html.append("  document.querySelectorAll('#lessonList li').forEach(li => {\n");
        html.append("    let title = li.getAttribute('data-title');\n");
        html.append("    li.style.display = title.includes(input) ? 'flex' : 'none';\n");
        html.append("  });\n");
        html.append("}\n");
        html.append("function toggleTheme() {\n");
        html.append("  let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';\n");
        html.append("  document.body.className = mode;\n");
        html.append("  localStorage.setItem('theme', mode);\n");
        html.append("}\n");
        html.append("function initPage() {\n");
        html.append("  document.body.className = localStorage.getItem('theme') || 'dark-mode';\n");
        html.append("  updateProgress();\n");
        html.append("}\n");
        html.append("</script></body></html>\n");

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
    int index;
    String title;
    String content;
    String link;
    String fileName;
    String currentListFileName;
    String prevFileName;
    String nextFileName;

    Lesson(int index, String title, String content, String link, String fileName, String currentListFileName) {
        this.index = index;
        this.title = title;
        this.content = content;
        this.link = link;
        this.fileName = fileName;
        this.currentListFileName = currentListFileName;
    }

    String toHtml(String topicId) {
        String cleanTitle = title.replace("==========", "-").replace("==", "--");
        String lessonKey = topicId + "_" + fileName;

        String prevBtn = (prevFileName != null) 
            ? "<a class='nav-btn' href='" + prevFileName + "'>⬅️ Bài trước</a>" 
            : "<span class='nav-btn disabled'>⬅️ Bài đầu</span>";

        String nextBtn = (nextFileName != null) 
            ? "<a class='nav-btn' href='" + nextFileName + "'>Bài sau ➡️</a>" 
            : "<span class='nav-btn disabled'>Hết bài 🏁</span>";

        String navBar = "<div class='nav-bar'>" 
            + prevBtn 
            + "<a class='nav-btn home-nav' href='../" + currentListFileName + "'>📋 Mục Lục</a>" 
            + nextBtn 
            + "</div>";

        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>\n" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
            "<title>" + cleanTitle + "</title>\n" +
            "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css'>\n" +
            "<script src='https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js'></script>\n" +
            "<script id='MathJax-script' async src='https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js'></script>\n" +
            "<style>\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; margin: 0; padding: 20px; transition: 0.3s; }\n" +
            ".dark-mode { background-color: #121212; color: #e0e0e0; }\n" +
            ".light-mode { background-color: #ffffff; color: #333333; }\n" +
            ".container { max-width: 950px; margin: auto; }\n" +
            "h1 { text-align: center; color: #3498db; margin: 20px 0; font-size: 26px; }\n" +
            ".nav-bar { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin: 20px 0; }\n" +
            ".nav-btn { text-decoration: none; padding: 10px 18px; background: #3498db; color: white; border-radius: 6px; font-weight: bold; font-size: 15px; display: inline-block; transition: 0.2s; }\n" +
            ".nav-btn:hover { background: #2980b9; }\n" +
            ".nav-btn.home-nav { background: #6c757d; }\n" +
            ".nav-btn.home-nav:hover { background: #5a6268; }\n" +
            ".nav-btn.disabled { background: #aaa; cursor: not-allowed; opacity: 0.5; }\n" +
            ".done-btn { display: block; margin: 15px auto; padding: 10px 24px; border: 2px solid #2ecc71; background: transparent; color: #2ecc71; border-radius: 25px; font-size: 16px; font-weight: bold; cursor: pointer; transition: 0.2s; }\n" +
            ".done-btn.completed { background: #2ecc71; color: white; }\n" +
            ".content-box { position: relative; margin: 20px 0; }\n" +
            "pre { padding: 20px; border-radius: 8px; white-space: pre-wrap; word-break: break-word; overflow-x: auto; font-family: 'Consolas', 'Courier New', monospace; font-size: 15px; }\n" +
            ".dark-mode pre { background: #1a1a1a; color: #f8f8f2; border: 1px solid #333; }\n" +
            ".light-mode pre { background: #f8f9fa; color: #212529; border: 1px solid #e9ecef; }\n" +
            ".copy-btn { position: absolute; top: 12px; right: 12px; padding: 6px 12px; font-size: 12px; background: #555; color: white; border: none; border-radius: 4px; cursor: pointer; opacity: 0.8; }\n" +
            ".copy-btn:hover { opacity: 1; background: #2ecc71; }\n" +
            ".theme-toggle-btn { display: block; margin: 20px auto; padding: 8px 16px; background: transparent; border: 1px solid #888; color: inherit; border-radius: 5px; cursor: pointer; }\n" +
            "</style></head><body onload='initLesson()'>\n" +
            "<div class='container'>\n" +
            navBar +
            "<h1>" + cleanTitle + "</h1>\n" +
            "<button id='doneButton' class='done-btn' onclick='toggleDone()'>Mark as Done ⚪</button>\n" +
            "<div class='content-box'>\n" +
            "<button class='copy-btn' onclick='copyContent()'>📋 Copy nội dung</button>\n" +
            "<pre><code id='lessonCode'>" + escapeHtml(content) + "</code></pre>\n" +
            "</div>\n" +
            navBar +
            "<button class='theme-toggle-btn' onclick='toggleTheme()'>🌓 Đổi Giao Diện</button>\n" +
            "</div>\n" +
            "<script>\n" +
            "const lessonKey = '" + lessonKey + "';\n" +
            "function toggleTheme() {\n" +
            "   let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';\n" +
            "   document.body.className = mode;\n" +
            "   localStorage.setItem('theme', mode);\n" +
            "}\n" +
            "function toggleDone() {\n" +
            "   let isDone = localStorage.getItem(lessonKey) === 'done';\n" +
            "   if (isDone) {\n" +
            "       localStorage.removeItem(lessonKey);\n" +
            "   } else {\n" +
            "       localStorage.setItem(lessonKey, 'done');\n" +
            "   }\n" +
            "   renderDoneStatus();\n" +
            "}\n" +
            "function renderDoneStatus() {\n" +
            "   let isDone = localStorage.getItem(lessonKey) === 'done';\n" +
            "   let btn = document.getElementById('doneButton');\n" +
            "   if (isDone) {\n" +
            "       btn.className = 'done-btn completed';\n" +
            "       btn.innerText = 'Đã Hoàn Thành ✅';\n" +
            "   } else {\n" +
            "       btn.className = 'done-btn';\n" +
            "       btn.innerText = 'Đánh dấu đã học ⚪';\n" +
            "   }\n" +
            "}\n" +
            "function copyContent() {\n" +
            "   let text = document.getElementById('lessonCode').innerText;\n" +
            "   navigator.clipboard.writeText(text).then(function() {\n" +
            "       alert('✅ Đã sao chép nội dung bài học!');\n" +
            "   });\n" +
            "}\n" +
            "function initLesson() {\n" +
            "   document.body.className = localStorage.getItem('theme') || 'dark-mode';\n" +
            "   renderDoneStatus();\n" +
            "   if (window.Prism) Prism.highlightAll();\n" +
            "}\n" +
            "</script></body></html>\n";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}