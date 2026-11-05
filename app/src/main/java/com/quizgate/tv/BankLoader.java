package com.quizgate.tv;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 题库加载器：内置 assets 题库 + U 盘导入题库。
 * U 盘约定目录：QuizGate/banks/*.json（各电视挂载点自动探测）。
 */
public class BankLoader {

    public static final String USB_SUBDIR = "QuizGate/banks";

    public static class LoadResult {
        public final List<QuestionBank> banks = new ArrayList<>();
        public int failedFiles = 0;

        public int totalQuestions() {
            int n = 0;
            for (QuestionBank b : banks) {
                n += b.size();
            }
            return n;
        }
    }

    /** 内置题库：app/src/main/assets/banks/ 下全部 JSON。 */
    public static LoadResult loadBuiltin(Context ctx) {
        LoadResult result = new LoadResult();
        try {
            String[] files = ctx.getAssets().list("banks");
            if (files == null) {
                return result;
            }
            for (String f : files) {
                if (!f.endsWith(".json")) {
                    continue;
                }
                try {
                    String text = readAsset(ctx, "banks/" + f);
                    parseInto(result, text);
                } catch (Exception e) {
                    result.failedFiles++;
                }
            }
        } catch (IOException e) {
            // assets 读取失败时静默，仅靠 U 盘题库
        }
        return result;
    }

    /**
     * 扫描常见外置挂载点下的 QuizGate/banks/*.json。
     *
     * 去重说明：不同挂载点可能是同一物理存储的不同视图
     * （/sdcard 与 getExternalStorageDirectory() 必然相同；
     * /mnt/media_rw 与 /storage/&lt;UUID&gt; 在许多电视盒子上也是同一块 U 盘）。
     * 早期版本按目录逐个扫，同一文件会被解析多次，题目数翻倍、
     * 锁屏页同一道题反复出现。这里按规范化路径去重。
     */
    public static LoadResult scanExternal(Context ctx) {
        LoadResult result = new LoadResult();
        Set<String> scannedDirs = new LinkedHashSet<>();
        Set<String> loadedFiles = new LinkedHashSet<>();
        List<File> roots = new ArrayList<>();
        File storage = new File("/storage");
        File[] children = storage.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory() && !c.getName().equals("self") && !c.getName().equals("emulated")) {
                    roots.add(c);
                }
            }
        }
        roots.add(new File("/mnt/media_rw"));
        roots.add(new File("/sdcard"));
        roots.add(android.os.Environment.getExternalStorageDirectory());

        for (File root : roots) {
            if (!root.isDirectory()) {
                continue;
            }
            File bankDir = new File(root, USB_SUBDIR);
            if (!scannedDirs.add(canonical(bankDir))) {
                continue;
            }
            File[] files = bankDir.listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (!f.isFile() || !f.getName().endsWith(".json")) {
                    continue;
                }
                if (!loadedFiles.add(canonical(f))) {
                    continue;
                }
                try {
                    parseInto(result, readFile(f));
                } catch (Exception e) {
                    result.failedFiles++;
                }
            }
        }
        return result;
    }

    /**
     * 规范化路径，用于去重；失败时退化为绝对路径（宁可重复也不漏扫）。
     * 包级可见：供 JVM 单元测试 BankLoaderTest 直接验证去重语义
     * （TV CI 在 API 30+ 上无法构造可读的多挂载点场景）。
     */
    static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    /** 内置 + U 盘全部加载。 */
    public static LoadResult loadAll(Context ctx) {
        LoadResult result = new LoadResult();
        LoadResult builtin = loadBuiltin(ctx);
        LoadResult external = scanExternal(ctx);
        result.banks.addAll(builtin.banks);
        result.banks.addAll(external.banks);
        result.failedFiles = builtin.failedFiles + external.failedFiles;
        return result;
    }

    private static void parseInto(LoadResult result, String text) throws org.json.JSONException {
        JSONObject json = new JSONObject(text);
        String type = json.optString("type");
        QuestionType qt = QuestionTypeRegistry.get(type);
        if (qt == null || qt.isGenerated()) {
            result.failedFiles++;
            return;
        }
        QuestionBank bank = qt.parse(json);
        if (bank.size() > 0) {
            result.banks.add(bank);
        }
    }

    private static String readAsset(Context ctx, String path) throws IOException {
        InputStream in = ctx.getAssets().open(path);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static String readFile(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), "UTF-8");
    }
}
