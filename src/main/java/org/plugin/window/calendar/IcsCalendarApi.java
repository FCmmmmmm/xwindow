package org.plugin.window.calendar;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ICS（iCalendar）日程读取：支持本地 {@code .ics} 文件与 http(s)/webcal 订阅链接。
 * <p>
 * 为什么在「系统日历」之外还要这条通道：读取 macOS 日历需要 Apple Events 授权，
 * 而授权主体是签名过的 App，插件寄生在 IDE 进程里未必拿得到（实测在非 App 进程下会直接返回
 * {@code -10004 权限违例}）。ICS 走的是纯文本解析，<b>不需要任何系统权限</b>，
 * 也能拿到企业微信 / Outlook / 公司共享日历导出的内容，因此是更可控的兜底方案。
 * <p>
 * 实现范围（刻意克制，避免引第三方库）：
 * <ul>
 *   <li>支持 {@code DTSTART}/{@code DTEND}/{@code SUMMARY}/{@code DURATION}；</li>
 *   <li>支持 RFC 5545 折行（CRLF + 空格/Tab 续行）与文本转义；</li>
 *   <li>重复事件只做启发式展开：{@code FREQ=DAILY} 与 {@code FREQ=WEEKLY}（含 BYDAY、INTERVAL、UNTIL），
 *       月度/年度重复一律跳过；</li>
 *   <li><b>不处理</b> {@code EXDATE} 排除与 {@code RECURRENCE-ID} 单次覆盖，
 *       所以被单独修改或取消的重复事件可能多显示一次。</li>
 * </ul>
 * 本类不依赖任何 IntelliJ API，便于单独编译与测试。
 */
public class IcsCalendarApi {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 8000;
    /** 订阅文件大小上限，防止误填超大文件把内存打满。 */
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    /** 读取结果：成功时 {@code error} 为 null。 */
    public record Result(List<CalendarEvent> events, String error) {

        public boolean isOk() {
            return error == null;
        }

        static Result ok(List<CalendarEvent> events) {
            return new Result(events, null);
        }

        static Result fail(String error) {
            return new Result(List.of(), error);
        }
    }

    /**
     * 读取今天的日程。必须在非 EDT 线程调用（本地文件 IO 或网络请求）。
     *
     * @param source 本地文件绝对路径，或 http(s)/webcal 订阅链接；为空返回未配置
     */
    public Result todayEvents(String source) {
        if (source == null || source.isBlank()) {
            return Result.fail("未配置 ICS 来源");
        }
        String trimmed = source.trim();
        try {
            String content = isRemote(trimmed)
                    ? fetch(normalizeUrl(trimmed))
                    : Files.readString(Path.of(trimmed), StandardCharsets.UTF_8);
            return Result.ok(parse(content, LocalDate.now()));
        } catch (java.nio.file.NoSuchFileException e) {
            return Result.fail("ICS 文件不存在：" + trimmed);
        } catch (Exception e) {
            return Result.fail("读取 ICS 失败：" + e.getMessage());
        }
    }

    static boolean isRemote(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("webcal://");
    }

    /** webcal:// 是日历订阅的惯用协议，实际传输仍是 https。 */
    static String normalizeUrl(String source) {
        return source.toLowerCase(Locale.ROOT).startsWith("webcal://")
                ? "https://" + source.substring("webcal://".length())
                : source;
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                return readLimited(in);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IllegalStateException("内容超过 5MB，已放弃解析");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * 解析 ICS 文本，返回与 {@code day} 相关的日程（含多日跨越与重复展开）。
     * <p>
     * 抽成 public static 是为了能脱离 IDE 单独验证——解析逻辑是纯文本处理，不需要运行环境。
     */
    public static List<CalendarEvent> parse(String ics, LocalDate day) {
        List<CalendarEvent> events = new ArrayList<>();
        if (ics == null || ics.isBlank()) {
            return events;
        }

        Map<String, String> props = new HashMap<>();
        String params = null;
        boolean inEvent = false;

        for (String rawLine : unfold(ics)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) {
                inEvent = true;
                props.clear();
                params = null;
                continue;
            }
            if (line.equalsIgnoreCase("END:VEVENT")) {
                inEvent = false;
                addIfRelevant(props, day, events);
                props.clear();
                continue;
            }
            if (!inEvent) {
                continue;
            }

            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String head = line.substring(0, colon);
            String value = line.substring(colon + 1);

            int semicolon = head.indexOf(';');
            String name = (semicolon < 0 ? head : head.substring(0, semicolon)).toUpperCase(Locale.ROOT);
            props.put(name, value);
            if (semicolon >= 0) {
                params = head.substring(semicolon + 1);
                // 只关心时区参数，其它参数（如 VALUE=DATE）由值形态自行判断
                for (String part : params.split(";")) {
                    if (part.toUpperCase(Locale.ROOT).startsWith("TZID=")) {
                        props.put("TZID", part.substring(5).replace("\"", "").trim());
                    }
                }
            }
        }
        events.sort(Comparator.comparing(CalendarEvent::start));
        return events;
    }

    /** RFC 5545 折行还原：把 "\n " / "\n\t" 续行拼回一行。 */
    private static List<String> unfold(String ics) {
        String normalized = ics.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : normalized.split("\n", -1)) {
            if (!current.isEmpty() && (line.startsWith(" ") || line.startsWith("\t"))) {
                current.append(line.substring(1));
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            current = new StringBuilder(line);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** 判断这条 VEVENT 今天是否出现，并换算成本地时间加入结果。 */
    private static void addIfRelevant(Map<String, String> props, LocalDate day, List<CalendarEvent> sink) {
        String dtStart = props.get("DTSTART");
        if (dtStart == null || dtStart.isBlank()) {
            return;
        }

        ParsedDate start = ParsedDate.parse(dtStart, props.get("TZID"));
        if (start == null) {
            return;
        }
        ParsedDate end = ParsedDate.parse(props.get("DTEND"), props.get("TZID"));
        if (end == null) {
            end = applyDuration(start, props.get("DURATION"));
        }

        String title = unescape(props.getOrDefault("SUMMARY", "")).strip();
        if (title.isEmpty()) {
            title = "（无标题日程）";
        }

        String rrule = props.get("RRULE");
        if (rrule == null || rrule.isBlank()) {
            addIfSameDay(start, end, title, day, sink);
            return;
        }
        addIfRecurring(start, end, title, day, rrule, sink);
    }

    /** 非重复事件：今天开始，或今天落在 [start, end) 区间内（多日会议/出差）。 */
    private static void addIfSameDay(ParsedDate start, ParsedDate end, String title,
                                     LocalDate day, List<CalendarEvent> sink) {
        LocalDate startDate = start.dateTime().toLocalDate();
        LocalDate endDate = end.dateTime().toLocalDate();
        if (startDate.isEqual(day)) {
            sink.add(new CalendarEvent(start.dateTime(), end.dateTime(), title, start.allDay(), "ICS"));
        } else if (startDate.isBefore(day) && endDate.isAfter(day)) {
            // 跨越今天的长事件：按全天呈现，避免出现 23:59 这种误导性时间
            sink.add(new CalendarEvent(day.atStartOfDay(), day.atTime(LocalTime.MAX),
                    title + "（多日）", true, "ICS"));
        }
    }

    /**
     * 重复事件启发式展开：只认 DAILY / WEEKLY，其余频率直接跳过（宁缺毋滥，
     * 月度重复一旦算错会天天给出不存在的会议）。
     */
    private static void addIfRecurring(ParsedDate start, ParsedDate end, String title,
                                       LocalDate day, String rrule, List<CalendarEvent> sink) {
        Map<String, String> rule = parseRule(rrule);
        String freq = rule.getOrDefault("FREQ", "").toUpperCase(Locale.ROOT);
        int interval = parseInt(rule.get("INTERVAL"), 1);

        LocalDate firstDate = start.dateTime().toLocalDate();
        if (day.isBefore(firstDate)) {
            return;
        }
        LocalDate until = parseUntil(rule.get("UNTIL"));
        if (until != null && day.isAfter(until)) {
            return;
        }

        boolean matches = switch (freq) {
            case "DAILY" -> ChronoUnit.DAYS.between(firstDate, day) % interval == 0;
            case "WEEKLY" -> matchesWeek(firstDate, day, interval, rule.get("BYDAY"));
            default -> false;
        };
        if (!matches) {
            return;
        }

        // 把时间套到今天：保留重复规则里原始的时刻与时长
        Duration length = Duration.between(start.dateTime(), end.dateTime());
        LocalDateTime todayStart = day.atTime(start.dateTime().toLocalTime());
        LocalDateTime todayEnd = start.allDay() ? day.atTime(LocalTime.MAX) : todayStart.plus(length);
        sink.add(new CalendarEvent(todayStart, todayEnd, title + "（重复）", start.allDay(), "ICS"));
    }

    private static boolean matchesWeek(LocalDate firstDate, LocalDate day, int interval, String byDay) {
        long weeks = ChronoUnit.WEEKS.between(firstDate, day);
        if (weeks % interval != 0) {
            return false;
        }
        if (byDay == null || byDay.isBlank()) {
            return firstDate.getDayOfWeek() == day.getDayOfWeek();
        }
        String code = switch (day.getDayOfWeek()) {
            case MONDAY -> "MO";
            case TUESDAY -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY -> "TH";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SU";
        };
        for (String part : byDay.split(",")) {
            if (part.trim().toUpperCase(Locale.ROOT).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> parseRule(String rrule) {
        Map<String, String> rule = new HashMap<>();
        for (String part : rrule.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                rule.put(part.substring(0, eq).toUpperCase(Locale.ROOT), part.substring(eq + 1));
            }
        }
        return rule;
    }

    /** UNTIL 可能是 20261106 / 20261106T090000Z，取其日期部分比较即可。 */
    private static LocalDate parseUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.strip().replace("Z", "");
        if (digits.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return raw == null ? fallback : Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static ParsedDate applyDuration(ParsedDate start, String rawDuration) {
        if (rawDuration == null || rawDuration.isBlank()) {
            return start.allDay()
                    ? new ParsedDate(start.dateTime().toLocalDate().atTime(LocalTime.MAX), true)
                    : new ParsedDate(start.dateTime().plusHours(1), false);
        }
        String value = rawDuration.trim().toUpperCase(Locale.ROOT);
        try {
            if (value.endsWith("D")) {
                int days = parseInt(value.substring(0, value.length() - 1).replace("P", ""), 1);
                return new ParsedDate(start.dateTime().plusDays(days), start.allDay());
            }
            Duration duration = Duration.parse(value.startsWith("P") ? value : "P" + value);
            return new ParsedDate(start.dateTime().plus(duration), start.allDay());
        } catch (Exception e) {
            return new ParsedDate(start.dateTime().plusHours(1), start.allDay());
        }
    }

    /** 反转义 iCalendar 文本：\\n、\\,、\\;、\\\\。 */
    private static String unescape(String text) {
        return text.replace("\\n", " ").replace("\\N", " ")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    /** 解析后的日期：附带「是否全天」标记。 */
    private record ParsedDate(LocalDateTime dateTime, boolean allDay) {

        /** 支持 20261106（全天）、20261106T090000、20261106T090000Z 三种写法。 */
        static ParsedDate parse(String raw, String tzid) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String value = raw.strip();
            try {
                if (value.length() == 8) {
                    LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyyMMdd"));
                    return new ParsedDate(date.atStartOfDay(), true);
                }

                boolean utc = value.endsWith("Z");
                String body = utc ? value.substring(0, value.length() - 1) : value;
                LocalDateTime wall = parseBody(body);
                if (wall == null) {
                    return null;
                }
                if (utc) {
                    // UTC 时间换成本地时区，否则「伦敦 02:00」会被当成北京时间 02:00
                    return new ParsedDate(wall.atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime(), false);
                }
                if (tzid != null && !tzid.isBlank()) {
                    try {
                        return new ParsedDate(wall.atZone(ZoneId.of(tzid))
                                .withZoneSameInstant(ZoneId.systemDefault())
                                .toLocalDateTime(), false);
                    } catch (Exception ignored) {
                        // TZID 不认识（如 Windows 时区名）时按本地墙钟处理，比丢弃更实用
                    }
                }
                return new ParsedDate(wall, false);
            } catch (Exception e) {
                return null;
            }
        }

        private static LocalDateTime parseBody(String body) {
            List<String> patterns = List.of("yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm", "yyyyMMdd");
            for (String pattern : patterns) {
                try {
                    return LocalDateTime.parse(body, DateTimeFormatter.ofPattern(pattern));
                } catch (Exception ignored) {
                    // 换下一个格式继续试
                }
            }
            return null;
        }
    }
}
