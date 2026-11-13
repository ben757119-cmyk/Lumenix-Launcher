package com.lumenix.launcher.util;

import com.lumenix.launcher.Paths;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class Log {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile Consumer<String> uiSink = s -> {};

    private Log() {}

    public static void setUiSink(Consumer<String> sink) {
        uiSink = sink != null ? sink : s -> {};
    }

    public static void info(String msg) { line("INFO", msg); }
    public static void warn(String msg) { line("WARN", msg); }
    public static void error(String msg) { line("ERROR", msg); }

    public static void error(String msg, Throwable t) {
        line("ERROR", msg + " — " + t.getMessage());
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        appendFile(sw.toString());
    }

    private static void line(String level, String msg) {
        String out = "[" + LocalDateTime.now().format(FMT) + "] [" + level + "] " + msg;
        System.out.println(out);
        try { uiSink.accept(out); } catch (Throwable ignored) {}
        appendFile(out + System.lineSeparator());
    }

    private static void appendFile(String text) {
        try {
            Paths.ensureDirs();
            var file = Paths.logs().resolve("launcher.log");
            Files.writeString(file, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {}
    }
}
