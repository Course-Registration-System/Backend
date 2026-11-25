package com.crs.backend.lecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 강의계획서 PDF 를 서버 디스크에 두고 읽습니다(LEC-03). 폴더는 crs.plan.dir(환경변수 PLAN_DIR, 기본 ./data)이고, 파일은
 * plans/{학기번호}/{과목코드}-{분반}.pdf 입니다. DB(lecture.plan_file)에는 이 폴더 기준 상대 경로만 저장합니다(시드와 같은 형식).
 */
@Component
public class PlanFileStorage {

    private final Path root;

    public PlanFileStorage(@Value("${crs.plan.dir:./data}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /** 강의의 계획서 상대 경로. 올린 파일 이름은 쓰지 않아 경로를 조작할 수 없고, 강의당 하나라 새로 올리면 덮어씁니다. */
    public static String relativePath(int semNo, String crsCd, int sectNo) {
        return "plans/" + semNo + "/" + crsCd + "-" + sectNo + ".pdf";
    }

    /** 내용을 그 경로에 씁니다. 같은 폴더의 임시 파일에 다 쓴 뒤 바꿔 끼우므로, 쓰다가 실패해도 이전 파일이 남습니다. */
    public void save(String relativePath, InputStream content) {
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            try {
                Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
                move(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("강의계획서를 저장하지 못했습니다: " + relativePath, e);
        }
    }

    /** 경로의 파일. DB 에 경로가 있어도 파일이 없으면(시드 등) 비어 있습니다. */
    public Optional<Path> find(String relativePath) {
        Path file = resolve(relativePath);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    // DB 값이 폴더 밖을 가리키지 않게 막는다(../ 등)
    private Path resolve(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("계획서 폴더 밖의 경로입니다: " + relativePath);
        }
        return file;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
