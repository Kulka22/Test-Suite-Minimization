package minimizer.coverage;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class PathUtils {
    private PathUtils() {
    }

    public static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            deleteRecursively(dir);
        }
        Files.createDirectories(dir);
    }

    public static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path folder, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(folder);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
