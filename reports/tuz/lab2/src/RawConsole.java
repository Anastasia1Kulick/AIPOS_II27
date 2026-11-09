import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Чтение строки с клавиатуры. В консоли строка возвращается по PgUp.
 * Если raw-режим недоступен, строка возвращается по Enter.
 */
public final class RawConsole implements Closeable {

    private static final int STD_INPUT_HANDLE = -10;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    private final InputStream in = System.in;
    private final OutputStream out = System.out;
    private final boolean lineMode;
    private final BufferedReader lines;
    private Runnable restore = () -> { };

    public RawConsole() {
        boolean raw = enableRaw();
        lineMode = !raw;
        lines = lineMode ? new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)) : null;
        if (lineMode) {
            System.out.println("Raw-консоль недоступна, строка отправляется по Enter.");
        }
    }

    public boolean lineMode() {
        return lineMode;
    }

    public String readLine() throws IOException {
        if (lineMode) {
            return lines.readLine();
        }
        StringBuilder buffer = new StringBuilder();
        boolean carriageReturn = false;
        while (true) {
            int value = in.read();
            if (value < 0) {
                return buffer.isEmpty() ? null : buffer.toString();
            }
            // PgUp приходит как ESC [ 5 ~. PgDn (ESC [ 6 ~) строку не отправляет.
            if (value == 0x1B) {
                if (readPageUp()) {
                    out.write('\r');
                    out.write('\n');
                    out.flush();
                    return buffer.toString();
                }
                carriageReturn = false;
                continue;
            }
            if (value == '\n' && carriageReturn) {
                carriageReturn = false;
                continue;
            }
            carriageReturn = value == '\r';
            if (value == '\r' || value == '\n') {
                buffer.append('\n');
                out.write('\r');
                out.write('\n');
                out.flush();
                continue;
            }
            if (value == 8 || value == 127) {
                eraseLast(buffer);
                continue;
            }
            if (value >= 0x20) {
                buffer.append(readCharacter(value));
            }
        }
    }

    static boolean pageUpSequence(String parameters, int command) {
        return command == '~' && (parameters.equals("5") || parameters.startsWith("5;"));
    }

    private boolean readPageUp() throws IOException {
        if (in.read() != '[') {
            return false;
        }
        StringBuilder parameters = new StringBuilder();
        int current;
        while ((current = in.read()) >= 0) {
            if (current >= 0x40 && current <= 0x7E) {
                return pageUpSequence(parameters.toString(), current);
            }
            parameters.append((char) current);
            if (parameters.length() > 16) {
                return false;
            }
        }
        return false;
    }

    private String readCharacter(int first) throws IOException {
        int length = first < 0x80 ? 1 : first < 0xE0 ? 2 : first < 0xF0 ? 3 : 4;
        byte[] bytes = new byte[length];
        bytes[0] = (byte) first;
        for (int index = 1; index < length; index++) {
            int next = in.read();
            if (next < 0) {
                break;
            }
            bytes[index] = (byte) next;
        }
        out.write(bytes);
        out.flush();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void eraseLast(StringBuilder buffer) throws IOException {
        if (buffer.isEmpty() || buffer.charAt(buffer.length() - 1) == '\n') {
            return;
        }
        buffer.setLength(buffer.length() - 1);
        out.write('\b');
        out.write(' ');
        out.write('\b');
        out.flush();
    }

    private boolean enableRaw() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                return enableWindowsRaw();
            }
            if (os.contains("nux") || os.contains("mac") || os.contains("nix")) {
                return enableUnixRaw();
            }
        } catch (Throwable exception) {
            System.err.println("Raw-режим не включён: " + exception.getMessage());
        }
        return false;
    }

    private boolean enableWindowsRaw() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup kernel = SymbolLookup.libraryLookup("Kernel32", Arena.global());
        MethodHandle getStdHandle = linker.downcallHandle(
                kernel.find("GetStdHandle").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MethodHandle getConsoleMode = linker.downcallHandle(
                kernel.find("GetConsoleMode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MethodHandle setConsoleMode = linker.downcallHandle(
                kernel.find("SetConsoleMode").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MemorySegment handle = (MemorySegment) getStdHandle.invokeExact(STD_INPUT_HANDLE);
        Arena arena = Arena.ofConfined();
        MemorySegment modeSlot = arena.allocate(ValueLayout.JAVA_INT);
        int read = (int) getConsoleMode.invokeExact(handle, modeSlot);
        if (read == 0) {
            arena.close();
            return false;
        }
        int saved = modeSlot.get(ValueLayout.JAVA_INT, 0);
        int raw = (saved & ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT)) | ENABLE_VIRTUAL_TERMINAL_INPUT;
        int written = (int) setConsoleMode.invokeExact(handle, raw);
        arena.close();
        if (written == 0) {
            return false;
        }
        restore = () -> {
            try {
                setConsoleMode.invokeExact(handle, saved);
            } catch (Throwable exception) {
                System.err.println("Не удалось вернуть режим консоли: " + exception.getMessage());
            }
        };
        return true;
    }

    private boolean enableUnixRaw() throws IOException, InterruptedException {
        String saved = shell("stty -g").trim();
        shell("stty raw -echo");
        restore = () -> {
            try {
                shell("stty " + saved);
            } catch (Exception exception) {
                System.err.println("Не удалось вернуть терминал: " + exception.getMessage());
            }
        };
        return true;
    }

    private static String shell(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("/bin/sh", "-c", command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        return output;
    }

    @Override
    public void close() {
        restore.run();
    }
}
