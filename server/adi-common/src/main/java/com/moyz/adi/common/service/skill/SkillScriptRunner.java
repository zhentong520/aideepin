package com.moyz.adi.common.service.skill;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 技能脚本运行器：以固定参数列表形式调用解释器（无 shell、无字符串拼接命令）。
 * 供 SkillToolService 在白名单校验后调用。
 *
 * 安全约定：command 必须是固定参数列表（解释器 + 白名单校验过的技能目录内脚本绝对路径），
 * 透传参数一律经环境变量传递；超时强杀；输出截断。
 */
public class SkillScriptRunner {

    private static final int MAX_OUTPUT_CHARS = 10_000;

    public static String run(List<String> command, Path workDir, Map<String, String> environment, long timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.environment().putAll(environment);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                    }
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "ERROR: script timeout after " + timeoutSeconds + "s";
            }
            int code = process.exitValue();
            String text = output.length() > MAX_OUTPUT_CHARS ? output.substring(0, MAX_OUTPUT_CHARS) : output.toString();
            return "exit_code: " + code + "\n" + (text.isBlank() ? "(no output)" : text.strip());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR: interrupted";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
