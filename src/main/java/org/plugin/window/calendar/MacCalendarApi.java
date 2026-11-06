package org.plugin.window.calendar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * macOS 系统日历读取桥：通过 {@code osascript} 让 Calendar.app 吐出今天的日程。
 * <p>
 * 为什么不用 EventKit / 直接读 {@code ~/Library/Calendars}：
 * <ul>
 *   <li>直接读文件会被 TCC 拒绝（实测 {@code Operation not permitted}），因为授权主体是签名过的 App，
 *       而插件只是寄生在 GoLand 进程里的一段代码，没有独立的 App 身份；</li>
 *   <li>EventKit 需要宿主 App 的 Info.plist 声明 {@code NSCalendarsUsageDescription}，GoLand 没有；</li>
 *   <li>AppleScript 走的是另一类权限（「自动化」），而 GoLand 的 Info.plist 已声明
 *       {@code NSAppleEventsUsageDescription}，所以这条路在 IDE 里是通的。</li>
 * </ul>
 * 代价：首次调用会弹一次系统授权框，归属对象是 <b>GoLand</b>（不是本插件），
 * 用户需要在「系统设置 → 隐私与安全性 → 自动化」里放行。授权是粘性的，之后的调用不会重复弹窗。
 * <p>
 * 本类一律不外抛异常：读不到就返回带原因的 {@link Result}，
 * 让调用方可以降级（只用手工提醒）而不是让整个「今日指引」失败。
 */
public class MacCalendarApi {

    /** 单次执行 osascript 的硬超时。Calendar.app 冷启动可能较慢，给 10s 余量。 */
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * 失败后的静默窗口。授权被拒时每次读取都要等超时，若不做抑制，
     * 用户每次点「今日指引」都要白等 10 秒——所以失败后 10 分钟内直接返回上次的错误，不再真跑。
     */
    private static final long SUPPRESS_WINDOW_MS = 10 * 60 * 1000L;

    private static volatile long suppressUntilMillis = 0L;
    private static volatile String suppressedError = "";

    /**
     * AppleScript 源码。要点：
     * <ul>
     *   <li>时间边界用「今天 00:00 → 明天 00:00」在脚本内算，避免 JVM 与 macOS 时区口径不一致；</li>
     *   <li>日期用 {@code «class isot»} 输出（如 {@code 2026-11-06T09:00:00}），
     *       不依赖系统语言环境，比 {@code as string} 稳；</li>
     *   <li>只取 start date 落在今天的，全天事件（start=00:00）自然包含；</li>
     *   <li>字段用 tab 分隔，避免标题里的空格干扰解析。</li>
     * </ul>
     */
    private static final String SCRIPT = """
            on run
            	set dayStart to (current date)
            	set time of dayStart to 0
            	set dayEnd to dayStart + 1 * days
            	set out to ""
            	tell application "Calendar"
            		repeat with cal in calendars
            			set evs to (every event of cal whose start date is greater than or equal to dayStart and start date is less than dayEnd)
            			repeat with e in evs
            				set sd to start date of e
            				set ed to end date of e
            				set out to out & (sd as «class isot» as string) & tab & (ed as «class isot» as string) & tab & (summary of e) & linefeed
            			end repeat
            		end repeat
            	end tell
            	return out
            end run
            """;

    /** 一条日程。时间为 ISO 本地时间字符串，如 {@code 2026-11-06T09:00:00}。 */
    public record Event(String startIso, String endIso, String title) {
    }

    /** 读取结果：成功时 {@code error} 为 null；失败时 {@code events} 为空。 */
    public record Result(List<Event> events, String error) {

        public boolean isOk() {
            return error == null;
        }

        static Result ok(List<Event> events) {
            return new Result(events, null);
        }

        static Result fail(String error) {
            return new Result(List.of(), error);
        }
    }

    /** 当前系统是否具备调用条件（macOS + 有 Calendar.app）。 */
    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") && Files.isDirectory(Path.of("/System/Applications/Calendar.app"));
    }

    /**
     * 读取今天的系统日历事件，按开始时间升序。
     * 必须在非 EDT 线程调用（启动进程 + 可能触发系统授权弹窗，最长阻塞 {@value #TIMEOUT_SECONDS}s）。
     */
    public Result todayEvents() {
        if (System.currentTimeMillis() < suppressUntilMillis) {
            return Result.fail(suppressedError + "（10 分钟内不再重试，可在设置页重新测试）");
        }
        if (!isSupported()) {
            return fail("当前系统不支持读取系统日历（仅 macOS 且需安装日历 App）");
        }

        Process process = null;
        try {
            process = new ProcessBuilder("osascript", "-e", SCRIPT).start();

            // 必须先起线程抽干 stdout/stderr 再等待：若先读流，会一直阻塞到进程退出，
            // 一旦卡在授权弹窗上，超时判断就永远走不到（管道缓冲区写满也会反向死锁）。
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outDrain = drain(process.getInputStream(), stdout);
            Thread errDrain = drain(process.getErrorStream(), stderr);

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return fail("读取系统日历超时。若屏幕上正显示「GoLand 想要控制“日历”」的授权框，"
                        + "请点击「好」后重新生成");
            }
            outDrain.join(1000);
            errDrain.join(1000);

            if (process.exitValue() != 0) {
                return fail(describeFailure(stderr.toString()));
            }
            return Result.ok(parse(stdout.toString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("读取系统日历被中断");
        } catch (Exception e) {
            return fail("调用 osascript 失败：" + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /** 记录失败原因并开启静默窗口，避免反复触发超时等待。 */
    private static Result fail(String error) {
        suppressedError = error;
        suppressUntilMillis = System.currentTimeMillis() + SUPPRESS_WINDOW_MS;
        return Result.fail(error);
    }

    /** 后台抽干一个流，避免子进程因管道写满而阻塞。 */
    private static Thread drain(java.io.InputStream in, StringBuilder sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (Exception ignored) {
                // 进程被强杀时流会抛异常，属预期，不影响结果判断
            }
        }, "xwindow-osascript-drain");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** 解析 tab 分隔的三列输出；脏行跳过，不因为一条怪数据整段失败。 */
    private static List<Event> parse(String stdout) {
        List<Event> events = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) {
            return events;
        }
        for (String line : stdout.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\t", 3);
            if (parts.length < 2) {
                continue;
            }
            String start = parts[0].strip();
            String end = parts[1].strip();
            String title = parts.length >= 3 ? parts[2].strip() : "";
            if (start.isEmpty()) {
                continue;
            }
            events.add(new Event(start, end, title.isEmpty() ? "（无标题日程）" : title));
        }
        events.sort(Comparator.comparing(Event::startIso));
        return events;
    }

    /** 把 osascript 的英文 stderr 翻成用户能照做的中文提示。 */
    private static String describeFailure(String stderr) {
        String raw = stderr == null ? "" : stderr.strip();
        if (raw.contains("-1743") || raw.toLowerCase().contains("not authorized")) {
            return "未授权访问系统日历：请在 系统设置 → 隐私与安全性 → 自动化 中"
                    + "勾选 GoLand 允许控制「日历」，然后重试";
        }
        if (raw.contains("-10004")) {
            // 实测：非 App 身份进程调用会直接返回权限违例，连授权弹窗的机会都没有
            return "系统拒绝了日历访问（-10004 权限违例）：当前进程没有可授权的 App 身份。"
                    + "建议改用 ICS 订阅（设置 → 工具箱日历 填 .ics 路径或订阅链接）";
        }
        if (raw.contains("-600") || raw.toLowerCase().contains("not running")) {
            return "日历 App 未运行且无法唤起，请先手动打开一次「日历」";
        }
        return "读取系统日历失败" + (raw.isEmpty() ? "" : "：" + raw);
    }
}
