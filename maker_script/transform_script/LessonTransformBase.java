import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LessonTransformBase {
	
    private static String filePath;
    private static String outputPath;
    private static String lessonDir;
    private static String baseGitHubUrl;
	private static String currentListFileName;

//    public static void main(String[] args) throws IOException {
//        List<Map<String, String>> configs = loadConfig("config.json");
//
//        for (Map<String, String> config : configs) {
//            // Gán giá trị từ JSON vào biến
//            filePath = config.get("filePath");
//            outputPath = config.get("outputPath");
//            lessonDir = config.get("lessonDir");
//            baseGitHubUrl = config.get("baseGitHubUrl");
//
//            System.out.println("🔹 Processing: " + filePath);
//            System.out.println("🔗 Base URL: " + baseGitHubUrl);
//
//            // 🔹 Xóa file HTML cũ
//            deleteFile(outputPath);
//
//            // 🔹 Xóa thư mục bài học cũ và tạo lại
//            deleteDirectory(lessonDir);
//            Files.createDirectories(Paths.get(lessonDir));
//
//            // 🔹 Đọc và xử lý bài học
//            List<Lesson> lessons = parseLessons(filePath, lessonDir);
//            generateHtml(outputPath, lessons);
//
//            System.out.println("✅ HTML file created: " + outputPath);
//        }
//    }
	
	 public static void main(String[] args) throws IOException {
        List<Config> configs = loadConfig("config.json");

        for (Config config : configs) {
            System.out.println("🔹 Processing: " + config.filePath);
            System.out.println("🔗 Base URL: " + config.baseGitHubUrl);
			
			 // Gán giá trị từ JSON vào biến
            filePath = config.filePath;
            outputPath = config.outputPath;
            lessonDir = config.lessonDir;
            baseGitHubUrl = config.baseGitHubUrl;
			
            // 🔹 Xóa file HTML cũ
            deleteFile(config.outputPath);

            // 🔹 Xóa thư mục bài học cũ và tạo lại
            deleteDirectory(config.lessonDir);
            Files.createDirectories(Paths.get(config.lessonDir));
			
			// 🔹 Cập nhật tên bài học hiện tại 
			currentListFileName = extractFileName(outputPath);
			
			// 🔹 Chuẩn hóa tiêu đề bài học
			normalizeLessonTitles(filePath);

            // 🔹 Đọc và xử lý bài học
            List<Lesson> lessons = parseLessons(config.filePath, config.lessonDir);
            generateHtml(config.outputPath, lessons);

            System.out.println("✅ HTML file created: " + config.outputPath);
        }
    }
	
	// 📌 Lấy ra tên của file từ đường dẫn 
	   public static String extractFileName(String outputPath) {
        Path path = Paths.get(outputPath);
        return path.getFileName().toString();
    }
	
	
	
// 📌 Đọc file JSON và parse dữ liệu thủ công (không dùng Gson)
    private static List<Config> loadConfig(String configFilePath) {
        List<Config> configs = new ArrayList<>();

        try {
            // Đọc toàn bộ nội dung file JSON thành một chuỗi
            String json = Files.readString(Paths.get(configFilePath));

            // Tìm tất cả các object trong JSON (giữa dấu { })
            Pattern pattern = Pattern.compile("\\{\\s*\"filePath\":\\s*\"([^\"]+)\",\\s*"
                                            + "\"outputPath\":\\s*\"([^\"]+)\",\\s*"
                                            + "\"lessonDir\":\\s*\"([^\"]+)\",\\s*"
                                            + "\"baseGitHubUrl\":\\s*\"([^\"]+)\"\\s*}");
            Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                String filePath = matcher.group(1);
                String outputPath = matcher.group(2);
                String lessonDir = matcher.group(3);
                String baseGitHubUrl = matcher.group(4);

                configs.add(new Config(filePath, outputPath, lessonDir, baseGitHubUrl));
            }
        } catch (IOException e) {
            System.err.println("❌ Lỗi đọc file cấu hình: " + e.getMessage());
        }

        return configs;
    }
	
	
	 // 🔹 Xóa file nếu tồn tại
    private static void deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.delete()) {
            System.out.println("✅ Đã xóa file: " + filePath);
        }
    }

    // 🔹 Xóa toàn bộ thư mục và file bên trong
    private static void deleteDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    deleteDirectory(file.getAbsolutePath());
                } else {
                    file.delete();
                }
            }
            dir.delete();
            System.out.println("✅ Đã xóa thư mục: " + dirPath);
        }
    }

    // 🔹 CHUẨN HÓA TIÊU ĐỀ BÀI HỌC (BỎ "Lesson X" CŨ, ĐÁNH SỐ LẠI)
    static void normalizeLessonTitles(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        List<String> updatedLines = new ArrayList<>();
        int lessonIndex = 1;

        for (String line : lines) {
            if (line.matches("//\\s*=+.*")) { // Dòng tiêu đề bài học
                // Loại bỏ toàn bộ "Lesson X ==" cũ nếu có
                String lessonName = line.replaceAll("//=+", "").trim(); // Bỏ `//=`
                lessonName = lessonName.replaceAll("Lesson \\d+ == ", ""); // Bỏ "Lesson X =="
				// xóa các ký tự lỗi  
                lessonName.replaceAll("Lession \\d+ == ", ""); // Bỏ "Lession X =="
						
                lessonName = lessonName.replaceAll("[=/]+$", "").trim(); // Bỏ `=` và `/` cuối

                // Tạo tiêu đề chuẩn mới
                line = "//==========Lesson " + lessonIndex + " == " + lessonName + " ==========//";
                lessonIndex++;
            }
            updatedLines.add(line);
        }

        // Ghi đè lại file gốc
        Files.write(Paths.get(filePath), updatedLines);
    }

    // 🔹 PHÂN TÍCH FILE & LƯU BÀI HỌC RIÊNG BIỆT
    static List<Lesson> parseLessons(String filePath, String lessonDir) throws IOException {
        List<Lesson> lessons = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));

        StringBuilder content = new StringBuilder();
        String title = "Untitled";
        int lessonCount = 1;

        for (String line : lines) {
            if (line.startsWith("//==========Lession ")) {  // Gặp dòng tiêu đề mới
                if (content.length() > 0) {
                    lessons.add(new Lesson(title, content.toString(), lessonDir + "/lesson" + lessonCount + ".html" ,currentListFileName ));
                    lessonCount++;
                    content.setLength(0);
                }
                title = line.replace("//==========", "").replaceAll("=+", "").trim();
            } else {
                content.append(line).append("\n");
            }
        }

        // Lưu bài cuối cùng nếu có
        if (content.length() > 0) {
            lessons.add(new Lesson(title, content.toString(), lessonDir + "/lesson" + lessonCount + ".html",currentListFileName));
        }

        return lessons;
    }

// 🔹 TẠO FILE HTML DANH SÁCH BÀI HỌC
static void generateHtml(String outputPath, List<Lesson> lessons) throws IOException {
    StringBuilder html = new StringBuilder();
    html.append("<html><head><title>Lesson List</title>")
        .append("<style>")
        .append("body { font-family: Arial, sans-serif; transition: background 0.3s, color 0.3s; }")
        .append(".dark-mode { background-color: #121212; color: #e0e0e0; }")
        .append(".light-mode { background-color: #ffffff; color: #333333; }")
        .append("h1 { text-align: center; color: #bb86fc; }")
        .append(".container { width: 80%; margin: auto; text-align: center; }")

        // 🔹 CSS cho nút Home
        .append(".home-btn { background: #ffcc00; color: #121212; border: none; padding: 10px 15px; ")
        .append("font-size: 16px; cursor: pointer; border-radius: 5px; font-weight: bold; margin-bottom: 15px; }")
        .append(".home-btn:hover { background: #ff9900; }")

        // 🔹 CSS cho các nút khác
        .append("button { background: #bb86fc; color: #121212; border: none; padding: 10px 15px; ")
        .append("font-size: 16px; cursor: pointer; border-radius: 5px; margin: 5px; }")
        .append("button:hover { background: #9b67e2; }")

        .append("ul { list-style: none; padding: 0; margin-left: 20px; }")
        .append("li { margin: 8px 0; text-align: left; }")
        .append("a { text-decoration: none; font-weight: bold; font-size: 18px; }")
        .append(".dark-mode a { color: #03dac6; } .light-mode a { color: #007bff; }")
        .append("</style></head><body onload='applyTheme()'>");

    html.append("<div class='container'>");

    // 🔹 Nút Home ở đầu trang
    html.append("<button class='home-btn' onclick='goHome()'>🏠 Home</button>");

    html.append("<h1>📖 Danh sách bài học</h1>");
    html.append("<button onclick='toggleTheme()'>🌙 Chuyển giao diện</button>");
    html.append("<button onclick='fixTitles()'>🔧 Sửa tiêu đề</button><ul>");
	
	for (Lesson lesson : lessons) {
    String lessonUrl = baseGitHubUrl + new File(lesson.link).getName(); // Chuyển sang URL GitHub
    html.append("<li><a href='").append(lessonUrl).append("'>")
        .append(lesson.title.replace("==========", "-").replace("==", "--"))
        .append("</a></li>");
    
    Files.write(Paths.get(lesson.link), lesson.toHtml().getBytes());
}

    html.append("</ul></div>");
    
    // 🔹 Script cho các chức năng
    html.append("<script>")
        .append("function toggleTheme() {")
        .append("let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';")
        .append("document.body.className = mode; localStorage.setItem('theme', mode);")
        .append("}")
        .append("function applyTheme() {")
        .append("let savedTheme = localStorage.getItem('theme') || 'dark-mode';")
        .append("document.body.className = savedTheme;")
        .append("}")
        .append("function fixTitles() { fetch('fix_titles').then(() => alert('✅ Tiêu đề đã được sửa!')); }")

        // 🔹 JavaScript cho nút Home
        .append("function goHome() { window.location.href = '../../index.html'; }")
        .append("</script>");

    html.append("</body></html>");
    Files.write(Paths.get(outputPath), html.toString().getBytes());
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

    @Override
    public String toString() {
        return "Config{" +
                "filePath='" + filePath + '\'' +
                ", outputPath='" + outputPath + '\'' +
                ", lessonDir='" + lessonDir + '\'' +
                ", baseGitHubUrl='" + baseGitHubUrl + '\'' +
                '}';
    }
}


// 🔹 CLASS BÀI HỌC
class Lesson {
    String title;
    String content;
    String link;
	String currentListFileName;

    Lesson(String title, String content, String link , String currentListFileName) {
        this.title = title;
        this.content = content;
        this.link = link;
		this.currentListFileName = currentListFileName;
    }
	String toHtml() {
    return "<html><head><title>" + title + "</title>" +
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
        "   font-size: 2em; padding: 20px 40px; " +
        "   background: #bb86fc; color: white; text-decoration: none; " +
        "   border-radius: 10px; display: inline-block; text-align: center; " +
        "}" +
        "#backTop:hover, #backBottom:hover { background: #9b67e2; }" +
        "button { font-size: 1.5em; padding: 15px 30px; " +
        "   background: #03dac6; color: #121212; border: none; " +
        "   cursor: pointer; border-radius: 5px; display: block; margin: 10px auto; }" +
        "button:hover { background: #02b8a3; }" +
        ".dark-mode a { color: #03dac6; } .light-mode a { color: #007bff; }" +
        "</style></head><body onload='applyTheme(); checkPageHeight()'>" +
        "<div class='container'>" +
        "<a id='backTop' href='../"+ currentListFileName +"'>🔙 Quay lại danh sách</a><br>" + 
        "<h1>" + title.replace("==========", "-").replace("==", "--") + "</h1>" +
        "<pre>" + content + "</pre>" +
        "<a id='backBottom' href='../"+ currentListFileName +"' style='display:none;'>🔙 Quay lại danh sách</a><br>" + 
        "<button onclick='toggleTheme()'>🌙 Chuyển giao diện</button>" +
        "</div>" +
        "<script>" +
        "function toggleTheme() {" +
        "   let mode = document.body.classList.contains('dark-mode') ? 'light-mode' : 'dark-mode';" +
        "   document.body.className = mode; localStorage.setItem('theme', mode);" +
        "   syncTheme();" +
        "}" +
        "function applyTheme() {" +
        "   let savedTheme = localStorage.getItem('theme') || 'dark-mode';" +
        "   document.body.className = savedTheme;" +
        "   syncTheme();" +
        "}" +
        "function syncTheme() {" +
        "   let preElement = document.querySelector('pre');" +
        "   if (document.body.classList.contains('dark-mode')) { preElement.style.background = '#1e1e1e'; preElement.style.color = '#e0e0e0'; }" +
        "   else { preElement.style.background = '#f5f5f5'; preElement.style.color = '#333333'; }" +
        "}" +
        "function checkPageHeight() {" +
        "   let contentHeight = document.body.scrollHeight;" +
        "   let windowHeight = window.innerHeight;" +
        "   if (contentHeight > windowHeight * 1.2) {" +
        "       document.getElementById('backBottom').style.display = 'block';" +
        "   } else {" +
        "       document.getElementById('backBottom').style.display = 'none';" +
        "   }" +
        "}" +
        "</script>" +
        "</body></html>";
}


}




