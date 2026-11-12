package com.som.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 소스 문자열 검사로 DEVELOPMENT_RULES·ARCHITECTURE 규칙을 지킨다(ArchUnit 없이). 작업 디렉터리 = 프로젝트 루트. */
class ArchitectureRulesTest {

    static final Path MAIN = Path.of("src/main/java/com/som");

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void engine_패키지는_repository나_JDBC_다른_기능_패키지에_의존하지_않는다() throws IOException {
        Pattern forbidden = Pattern.compile(
                "import\\s+(org\\.springframework\\.(jdbc|data)|jakarta\\.persistence|com\\.som\\.(?!engine\\.)[a-z]+)");
        List<Path> violations = javaFiles(MAIN.resolve("engine")).stream()
                .filter(p -> forbidden.matcher(read(p)).find()).toList();
        assertThat(violations).isEmpty();
    }

    @Test
    void 현재_시각은_Clock으로만_얻는다() throws IOException {
        Pattern noClockNow = Pattern.compile(
                "\\b(LocalDate|LocalDateTime|LocalTime|Instant|OffsetDateTime|ZonedDateTime)\\.now\\(\\s*\\)"
                        + "|System\\.currentTimeMillis\\(\\)|new\\s+Date\\(\\s*\\)");
        List<Path> violations = javaFiles(MAIN).stream().filter(p -> noClockNow.matcher(read(p)).find()).toList();
        assertThat(violations).isEmpty();
    }

    @Test
    void 컨트롤러는_요청에서_userId를_받지_않는다() throws IOException {
        Pattern userIdParam = Pattern.compile("@(PathVariable|RequestParam|RequestHeader)[^,)]*\\bLong\\s+userId");
        List<Path> violations = javaFiles(MAIN).stream()
                .filter(p -> p.toString().endsWith("Controller.java"))
                .filter(p -> userIdParam.matcher(read(p)).find()).toList();
        assertThat(violations).isEmpty();
    }
}
