package com.quizgate.tv;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * BankLoader.canonical() 去重回归防线。
 *
 * 背景（2026-11-05 TV CI 首跑暴露）：不同挂载点常是同一物理存储的
 * 不同视图——/sdcard 与 getExternalStorageDirectory() 必然相同，
 * /mnt/media_rw 与 /storage/&lt;UUID&gt; 在许多电视盒子上也是同一块 U 盘。
 * 不按规范化路径去重时，同一 JSON 会被解析多次，题目数翻倍、
 * 锁屏页同一道题反复出现。修复按 canonical 路径双重去重
 * （scannedDirs + loadedFiles）。
 *
 * TV CI 在 API 30+ 上因分区存储读不到外置目录，无法构造可读的
 * 多挂载点场景，因此去重语义由本 JVM 单元测试守护。
 */
public class BankLoaderTest {

    @Test
    public void equivalentPathsShareOneCanonical() throws IOException {
        File base = new File("/sdcard/QuizGate/banks");
        // 冗余段 "."
        assertEquals(BankLoader.canonical(base),
                BankLoader.canonical(new File("/sdcard/./QuizGate/banks")));
        // 末尾斜杠与内部冗余段
        assertEquals(BankLoader.canonical(base),
                BankLoader.canonical(new File("/sdcard/QuizGate/./banks/")));
        // 双层冗余
        assertEquals(BankLoader.canonical(base),
                BankLoader.canonical(new File("/sdcard/./QuizGate/./banks")));
    }

    @Test
    public void differentPathsKeepDistinctCanonical() throws IOException {
        // 同名前缀不同目录，不得误判为同一路径
        assertNotEquals(BankLoader.canonical(new File("/sdcard/QuizGate/banks")),
                BankLoader.canonical(new File("/sdcard/QuizGate/banks2")));
        // 不同 U 盘挂载点是不同文件（只有 canonical 相同才去重）
        assertNotEquals(BankLoader.canonical(new File("/storage/1234-5678/QuizGate/banks")),
                BankLoader.canonical(new File("/storage/ABCD-EF01/QuizGate/banks")));
    }

    @Test
    public void canonicalIsDeterministicForMissingPaths() throws IOException {
        // 不存在的文件也要有确定性结果：去重集合键稳定，不会因时序抖动漏扫/重扫
        File missing = new File("/nonexistent-quizgate-dir/QuizGate/banks");
        assertEquals(missing.getCanonicalPath(), BankLoader.canonical(missing));
        assertEquals(BankLoader.canonical(missing), BankLoader.canonical(missing));
        // 跨平台校验：规范化结果应保留完整目录结构（Windows 下分隔符为 \）
        String normalized = BankLoader.canonical(missing).replace('\\', '/');
        assertTrue("规范化路径应保留目录结构，实际：" + normalized,
                normalized.endsWith("nonexistent-quizgate-dir/QuizGate/banks"));
    }
}
