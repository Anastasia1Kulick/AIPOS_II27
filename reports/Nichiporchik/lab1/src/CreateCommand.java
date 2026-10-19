import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CreateCommand {
    public static final String END_MESSAGE = "Session finished: file cannot be created or its size is zero";

    public static boolean isCreate(String line) {
        String[] parts = line.trim().split("\\s+", 2);
        return parts[0].equalsIgnoreCase("create");
    }

    public static long execute(String line) {
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length < 3) {
            return -1;
        }
        byte[] data = parts[2].getBytes(StandardCharsets.UTF_8);
        if (data.length == 0) {
            return -1;
        }
        try {
            Path path = Paths.get(parts[1]);
            if (path.isAbsolute() || path.getNameCount() != 1) {
                return -1;
            }
            Files.write(path, data);
            long size = Files.size(path);
            return size > 0 ? size : -1;
        } catch (IOException | InvalidPathException e) {
            return -1;
        }
    }
}
