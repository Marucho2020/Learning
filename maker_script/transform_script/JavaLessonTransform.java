import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class JavaLessonTransform {
    
    private static final String filePath = "D:\\Dev\\Java\\Learn\\JavaSliveLearn.txt";
    private static final String outputPath = "D:\\Dev\\Learning-all-git-page\\JavaLearn\\java-learning-list.html";
    private static final String lessonDir = "D:\\Dev\\Learning-all-git-page\\JavaLearn\\lessons";
    private static final String baseGitHubUrl = "https://marucho2020.github.io/Learning/JavaLearn/lessons/";

    public static void main(String[] args) throws IOException {
        System.out.println("Base URL: " + baseGitHubUrl);

        // 🔹 Xóa file HTML cũ
        deleteFile(outputPath);

        // 🔹 Xóa thư mục bài học cũ và tạo lại
        deleteDirectory(lessonDir);
        Files.createDirectories(Paths.get(lessonDir));

        // 🔹 Đọc và xử lý bài học
        List<Lesson> lessons = parseLessons(filePath, lessonDir);
        generateHtml(outputPath, lessons);

        System.out.println("✅ HTML file created: " + outputPath);
    }

    // 📌 Xóa file cũ nếu tồn tại
    private static void deleteFile(String path) {
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            System.err.println("❌ Lỗi khi xóa file: " + e.getMessage());
        }
    }

    // 📌 Xóa thư mục bài học cũ nếu tồn tại
    private static void deleteDirectory(String path) {
        try {
            Files.walk(Paths.get(path))
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("❌ Lỗi khi xóa thư mục: " + e.getMessage());
        }
    }

    // 📌 Lớp Lesson để lưu trữ tiêu đề và nội dung bài học
    static class Lesson {
        int level;
        String title;
        String id;
        StringBuilder content;
        List<Lesson> children;

        Lesson(int level, String title) {
            this.level = level;
            this.title = title;
            this.id = title.toLowerCase().replace(" ", "-").replace("#", "");
            this.content = new StringBuilder();
            this.children = new ArrayList<>();
        }
    }

    // 📌 Phân tích bài học từ file TXT, tạo các file HTML cho từng bài học
    private static List<Lesson> parseLessons(String filePath, String lessonDir) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        Pattern pattern = Pattern.compile("^(#{1,5})\\s*(.+)$");
        List<Lesson> rootLessons = new ArrayList<>();
        Stack<Lesson> stack = new Stack<>();

        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                int level = matcher.group(1).length(); // Số lượng dấu #
                String title = matcher.group(2).trim();
                Lesson newLesson = new Lesson(level, title);

                // Xác định vị trí trong cây
                while (!stack.isEmpty() && stack.peek().level >= level) {
                    stack.pop();
                }

                if (stack.isEmpty()) {
                    rootLessons.add(newLesson);
                } else {
                    stack.peek().children.add(newLesson);
                }

                stack.push(newLesson);
            } else {
                if (!stack.isEmpty()) {
                    stack.peek().content.append(line).append("\n");
                }
            }
        }

        // Tạo file HTML cho từng bài học
        for (Lesson lesson : rootLessons) {
            generateLessonHtml(lesson, lessonDir);
        }

        return rootLessons;
    }

	// 📌 Tạo file HTML cho từng bài học
	private static void generateLessonHtml(Lesson lesson, String lessonDir) throws IOException {
		String fileName = sanitizeFileName(lesson.title) + ".html"; // Chuẩn hóa tên file
		String filePath = lessonDir + "/" + fileName;
		
		String lessonHtml = "<html><head><title>" + lesson.title + "</title></head><body>"
							+ "<h1>" + lesson.title + "</h1>"
							+ "<pre>" + lesson.content + "</pre>"
							+ "</body></html>";
	
		Files.write(Paths.get(filePath), lessonHtml.getBytes());
		System.out.println("📄 Created: " + filePath);
	}
	
	// 📌 Chuẩn hóa tiêu đề thành tên file hợp lệ
	private static String sanitizeFileName(String title) {
		return title.replaceAll("[\\\\/:*?\"<>|]", "")  // Loại bỏ ký tự không hợp lệ
					.replaceAll("\\s+", "-")           // Thay khoảng trắng bằng dấu -
					.replaceAll("[^\\p{ASCII}]", "");  // Loại bỏ ký tự Unicode nếu cần
	}


    // 📌 Tạo trang index danh sách bài học
    private static void generateHtml(String outputPath, List<Lesson> lessons) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Danh sách bài học</title>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; }")
            .append("ul { list-style-type: none; padding-left: 20px; }")
            .append("h1, h2, h3, h4, h5 { color: #333; }")
            .append(".lesson-content { background: #f5f5f5; padding: 10px; border-radius: 5px; }")
            .append("</style></head><body>")
            .append("<h1>Danh sách bài học</h1>")
            .append(buildHtmlTree(lessons))
            .append("</body></html>");

        Files.write(Paths.get(outputPath), html.toString().getBytes());
    }

    // 📌 Đệ quy tạo danh sách HTML từ danh sách bài học
    private static String buildHtmlTree(List<Lesson> lessons) {
        StringBuilder html = new StringBuilder("<ul>");
        for (Lesson lesson : lessons) {
            String lessonUrl = baseGitHubUrl + lesson.id + ".html";
            html.append("<li>")
                .append("<a href='").append(lessonUrl).append("'>").append(lesson.title).append("</a>");

            if (!lesson.children.isEmpty()) {
                html.append(buildHtmlTree(lesson.children));
            }

            html.append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }
}
