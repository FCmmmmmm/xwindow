package org.plugin.window.content;

import org.plugin.window.calendar.CalendarEvent;
import org.plugin.window.calendar.CalendarSettings;
import org.plugin.window.calendar.IcsCalendarApi;
import org.plugin.window.calendar.MacCalendarApi;
import org.plugin.window.reminder.ReminderAgenda;
import org.plugin.window.rpc.deepseek.DeepSeekChatApi;
import org.plugin.window.settings.DeepSeekApiKeyStore;
import org.plugin.window.settings.GaodeSettings;
import org.plugin.window.settings.UserProfileSettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「今日指引」的编排层：本地采集上下文 → 拼提示词 → 调 DeepSeek → 返回 Markdown。
 * <p>
 * 分层职责与其它卡片一致：{@code content/*} 只做数据加工与文案拼装，不碰 UI；
 * 面板拿到 Markdown 后由 {@link MarkdownContent} 渲成 HTML。
 * <p>
 * 结果按天缓存在内存里：同一天再次打开弹窗直接出旧结果，点「重新生成」才重新计费。
 */
public final class DailyFortuneContent {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 系统提示词：角色 + 硬性约束 + 固定结构。
     * 结构定死是刻意的——Markdown 标题稳定，弹窗排版才不会忽长忽短。
     */
    private static final String SYSTEM_PROMPT = """
            你是一位懂中国传统择日文化、又熟悉程序员日常节奏的生活顾问。
            用户会给你今天的日期、所在地天气实况、未来几天预报，以及今天已有的日程提醒。
            请据此写一份务实、简短、带点古意的「今日指引」。

            硬性要求：
            1. 只依据用户给出的信息推断，绝不编造未提供的会议、人名、地点或数字。
            2. 全程简体中文，输出 Markdown；只允许使用 ## 和 ### 标题、列表（- 开头）、**加粗**、段落。
               禁止使用表格、代码块、引用块、链接。
            3. 结构必须严格按下面的顺序与小标题：

            ## 今日总览
            先用一句话定调（不超过 25 字），再另起一行给出：**顺遂指数 xx/100 · 吉凶等级**（等级从「大吉、中吉、小吉、平、小凶」中选一），
            再用一到两句话说明判断依据（天气、温差、日程密度、作息节律中挑最相关的）。

            ### 宜
            三条，每条一行，以 - 开头，必须是今天能立刻执行的具体动作，不要空话。

            ### 忌
            三条，每条一行，以 - 开头，针对今天最可能踩的坑（如温差大易感冒、日程过密易漏事）。

            ### 天气与出行
            两到三句话：穿衣、通勤方式、是否需要带伞或防晒。必须引用给出的气温与天气。

            ### 日程提醒
            按时间顺序点出今天的关键节点；如果日程里存在时间冲突或过密时段，明确指出最容易出问题的那一环。
            若今天没有日程，就给出一个节奏建议。

            ### 一句话
            一句不超过 30 字、带点古意的寄语，不要重复上面的内容。

            语气：像一个了解你的老朋友，轻松、具体，不说教、不制造焦虑。
            占断仅供娱乐，不要出现医疗、投资、法律等专业建议。
            """;

    private static volatile String cachedDate = "";
    private static volatile String cachedMarkdown = "";

    /** 一次生成的结果。{@code cached} 表示是当天复用的旧结果，未产生新的接口调用。 */
    public record Fortune(String markdown, String error, boolean cached) {

        public boolean isOk() {
            return error == null;
        }
    }

    private DailyFortuneContent() {
    }

    /** 当天已生成的结果；跨天或从未生成时返回空串。 */
    public static String currentCache() {
        return LocalDate.now().toString().equals(cachedDate) ? cachedMarkdown : "";
    }

    /**
     * 生成今日指引。必须在非 EDT 线程调用（内含多次网络请求）。
     *
     * @param forceRefresh true 时忽略当天缓存，强制重新请求模型
     */
    public static Fortune generate(boolean forceRefresh) {
        if (!forceRefresh) {
            String cache = currentCache();
            if (!cache.isBlank()) {
                return new Fortune(cache, null, true);
            }
        }

        String apiKey = DeepSeekApiKeyStore.getKey();
        if (apiKey.isBlank()) {
            return new Fortune(null, "未配置 DeepSeek API Key，请到 设置 → 工具箱 DeepSeek 中填写", false);
        }

        String userPrompt = buildUserPrompt();
        DeepSeekChatApi.Result result = new DeepSeekChatApi().Chat(apiKey, SYSTEM_PROMPT, userPrompt);
        if (!result.isOk()) {
            return new Fortune(null, result.error(), false);
        }

        cachedDate = LocalDate.now().toString();
        cachedMarkdown = result.content();
        return new Fortune(cachedMarkdown, null, false);
    }

    /** 采集本地上下文并拼成提示词的「资料段落」。 */
    private static String buildUserPrompt() {
        LocalDate today = LocalDate.now();
        String cityCode = GaodeSettings.getInstance().getCityCode();
        String cityName = GaodeSettings.getInstance().getCityName();
        String userName = UserProfileSettings.getInstance().getUserName();

        StringBuilder prompt = new StringBuilder();
        prompt.append("【基本信息】\n");
        prompt.append("称呼：").append(userName == null || userName.isBlank() ? "朋友" : userName).append('\n');
        prompt.append("今天：").append(today.format(DATE_FMT)).append(' ')
                .append(weekdayCn(today)).append('\n');
        prompt.append("现在时间：").append(LocalTime.now().format(TIME_FMT)).append('\n');
        prompt.append("所在城市：").append(cityName).append("\n\n");

        prompt.append("【今日天气实况】\n");
        WeatherContent.LiveDetail live = WeatherContent.getLiveDetail(cityCode);
        if (live == null) {
            prompt.append("（天气数据获取失败，请在建议中回避对天气的具体断言）\n");
        } else {
            prompt.append(String.format("%s，气温 %s°C，湿度 %s%%，%s风 %s 级（%s 发布）\n",
                    nullToDash(live.weather()),
                    nullToDash(live.temperature()),
                    nullToDash(live.humidity()),
                    nullToDash(live.windDirection()),
                    nullToDash(live.windPower()),
                    nullToDash(live.reportTime())));
        }
        prompt.append('\n');

        prompt.append("【未来几天预报（数据源仅提供 4 天，属于预报，不代表今天一定如此）】\n");
        List<WeatherContent.DayForecast> days = WeatherContent.getForecast(cityCode);
        if (days.isEmpty()) {
            prompt.append("（预报获取失败，请在建议中回避对后续天气的具体断言）\n");
        } else {
            for (WeatherContent.DayForecast day : days) {
                prompt.append(String.format("%s %s：白天 %s %s°C，夜间 %s %s°C，%s风 %s 级\n",
                        nullToDash(day.date()),
                        nullToDash(day.week()),
                        nullToDash(day.dayWeather()),
                        nullToDash(day.dayTemp()),
                        nullToDash(day.nightWeather()),
                        nullToDash(day.nightTemp()),
                        nullToDash(day.dayWind()),
                        nullToDash(day.dayPower())));
            }
        }
        prompt.append('\n');

        prompt.append("【今天的日程】\n");
        appendSchedule(prompt);
        prompt.append('\n');

        prompt.append("【作息】\n");
        prompt.append("工作时段：").append(UserProfileSettings.getInstance().getWorkStartTime())
                .append(" – ").append(UserProfileSettings.getInstance().getWorkEndTime()).append('\n');
        prompt.append('\n');
        prompt.append("请据此输出今天的指引。");

        return prompt.toString();
    }

    /**
     * 写「今天的日程」段落：合并 ICS、macOS 系统日历与插件定时提醒，按时间排序并去重。
     * <p>
     * 三个来源都是「锦上添花」：任一读失败只往提示词里加一行说明，
     * 仍用其余来源继续生成——不能因为一个可选数据源让整个功能不可用。
     */
    private static void appendSchedule(StringBuilder prompt) {
        List<AgendaLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        StringBuilder problems = new StringBuilder();
        CalendarSettings settings = CalendarSettings.getInstance();

        String icsSource = settings.getIcsSource();
        if (icsSource != null && !icsSource.isBlank()) {
            IcsCalendarApi.Result ics = new IcsCalendarApi().todayEvents(icsSource);
            if (ics.isOk()) {
                addEvents(ics.events(), lines, seen);
            } else {
                problems.append("（ICS 日历不可用：").append(ics.error()).append("）\n");
            }
        }

        if (settings.isSystemCalendarEnabled()) {
            MacCalendarApi.Result calendar = new MacCalendarApi().todayEvents();
            if (calendar.isOk()) {
                List<CalendarEvent> events = new ArrayList<>();
                for (MacCalendarApi.Event event : calendar.events()) {
                    events.add(toEvent(event));
                }
                addEvents(events, lines, seen);
            } else {
                problems.append("（系统日历不可用：").append(calendar.error()).append("）\n");
            }
        }

        for (ReminderAgenda.Entry entry : ReminderAgenda.todayAgenda()) {
            String text = entry.time() + " " + entry.text() + "（插件提醒）";
            if (seen.add(text)) {
                lines.add(new AgendaLine(entry.time(), text));
            }
        }

        lines.sort(Comparator.comparing(AgendaLine::sortKey));

        if (lines.isEmpty()) {
            prompt.append("（今天没有日程，也没有配置提醒）\n");
        } else {
            for (AgendaLine line : lines) {
                prompt.append("- ").append(line.text()).append('\n');
            }
        }
        prompt.append(problems);
    }

    /** 日程行：{@code sortKey} 用于排序（"HH:mm"），{@code text} 为最终展示文案。 */
    private record AgendaLine(String sortKey, String text) {
    }

    /**
     * 把日历事件格式化后并入日程列表。
     * 去重键用「时间 + 标题」：同一条日程同时出现在系统日历与导出文件里是常态，
     * 但它对模型是同一件事，重复列两遍只会让模型误判日程密度。
     */
    private static void addEvents(List<CalendarEvent> events, List<AgendaLine> lines, Set<String> seen) {
        for (CalendarEvent event : events) {
            String source = "（" + event.source() + "）";
            String text = event.allDay()
                    ? "全天 " + event.title() + source
                    : startHHmm(event.start()) + "-" + startHHmm(event.end()) + " " + event.title() + source;
            if (seen.add(text)) {
                lines.add(new AgendaLine(startHHmm(event.start()), text));
            }
        }
    }

    /** 把 osascript 的字符串时间转成 {@link CalendarEvent}。 */
    private static CalendarEvent toEvent(MacCalendarApi.Event event) {
        LocalDateTime start = parseIso(event.startIso());
        LocalDateTime end = parseIso(event.endIso());
        return new CalendarEvent(start, end, event.title(), isAllDay(start, end), "macOS 日历");
    }

    private static LocalDateTime parseIso(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(iso.trim());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private static boolean isAllDay(LocalDateTime start, LocalDateTime end) {
        return start.toLocalTime().equals(LocalTime.MIDNIGHT)
                && (end == null || end.toLocalTime().equals(LocalTime.MIDNIGHT));
    }

    private static String startHHmm(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FMT);
    }

    /** "星期五"。用 {@link TextStyle#FULL} 拿到中文星期全称，不依赖系统 Locale。 */
    private static String weekdayCn(LocalDate date) {
        return "星期" + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "--" : value;
    }
}
