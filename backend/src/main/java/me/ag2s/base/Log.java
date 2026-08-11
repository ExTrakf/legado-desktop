package me.ag2s.base;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 桌面版 android.util.Log 等价替代（epublib fork 原用 android.util.Log）。
 * 仅 d/i/v/w/e 静态方法，输出到 java.util.logging。
 */
public final class Log {

    private Log() {
    }

    private static Logger logger(String tag) {
        String name = "epublib." + tag;
        return Logger.getLogger(name);
    }

    public static int d(String tag, String msg) {
        logger(tag).log(Level.FINE, msg);
        return 0;
    }

    public static int d(String tag, String msg, Throwable tr) {
        logger(tag).log(Level.FINE, msg, tr);
        return 0;
    }

    public static int v(String tag, String msg) {
        logger(tag).log(Level.FINEST, msg);
        return 0;
    }

    public static int v(String tag, String msg, Throwable tr) {
        logger(tag).log(Level.FINEST, msg, tr);
        return 0;
    }

    public static int i(String tag, String msg) {
        logger(tag).log(Level.INFO, msg);
        return 0;
    }

    public static int i(String tag, String msg, Throwable tr) {
        logger(tag).log(Level.INFO, msg, tr);
        return 0;
    }

    public static int w(String tag, String msg) {
        logger(tag).log(Level.WARNING, msg);
        return 0;
    }

    public static int w(String tag, String msg, Throwable tr) {
        logger(tag).log(Level.WARNING, msg, tr);
        return 0;
    }

    public static int w(String tag, Throwable tr) {
        logger(tag).log(Level.WARNING, tr.getMessage(), tr);
        return 0;
    }

    public static int e(String tag, String msg) {
        logger(tag).log(Level.SEVERE, msg);
        return 0;
    }

    public static int e(String tag, String msg, Throwable tr) {
        logger(tag).log(Level.SEVERE, msg, tr);
        return 0;
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
