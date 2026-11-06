package org.plugin.window.reminder;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把「定时提醒配置（cron）」反推成「今天的具体日程」，供今日指引面板喂给大模型。
 * <p>
 * 提醒本身是 cron 表达式（如工作日 9:00），对人没有可读性，对大模型更是噪音；
 * 所以这里在本地把今天 00:00–24:00 之间的触发点全部枚举出来，转成「09:00 站会」这样的清单。
 * <p>
 * 单条提醒最多枚举 {@link #MAX_OCCURRENCES} 次（如「每 5 分钟」这类高频提醒），
 * 超出后只保留前若干条并标记为高频，避免提示词被同一句话刷屏。
 */
public final class ReminderAgenda {

    /** 单条提醒单日最多展开的触发次数，防止高频 cron 把提示词撑爆。 */
    private static final int MAX_OCCURRENCES = 8;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** 一条今日日程：{@code time} 为 "HH:mm"，{@code text} 为提醒内容。 */
    public record Entry(String time, String text) {
    }

    private ReminderAgenda() {
    }

    /**
     * 计算今天的日程清单，按时间升序。无启用提醒或全部解析失败时返回空列表。
     */
    public static List<Entry> todayAgenda() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        ZonedDateTime dayStart = today.atStartOfDay(zone);
        ZonedDateTime dayEnd = dayStart.plusDays(1);

        List<Entry> entries = new ArrayList<>();
        for (ReminderItem item : ReminderSettings.getInstance().getReminders()) {
            if (!item.isEnabled() || item.getText() == null || item.getText().isBlank()) {
                continue;
            }
            collectOccurrences(item, dayStart, dayEnd, entries);
        }

        entries.sort(Comparator.comparing(Entry::time));
        return entries;
    }

    /** 枚举单条提醒在 [dayStart, dayEnd) 内的触发点并追加到 {@code sink}。 */
    private static void collectOccurrences(ReminderItem item, ZonedDateTime dayStart,
                                          ZonedDateTime dayEnd, List<Entry> sink) {
        try {
            Cron cron = CronSupport.parse(item.getCron());
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            // 从 00:00 前一秒起算，保证「正好 00:00 触发」的提醒也能被枚举到
            ZonedDateTime cursor = dayStart.minusSeconds(1);
            int count = 0;
            while (count < MAX_OCCURRENCES) {
                var next = executionTime.nextExecution(cursor);
                if (next.isEmpty()) {
                    return;
                }
                ZonedDateTime at = next.get();
                if (!at.isBefore(dayEnd)) {
                    return;
                }
                sink.add(new Entry(at.format(TIME_FMT), item.getText().trim()));
                count++;
                cursor = at;
            }
            // 触达上限说明是高频提醒，补一条说明让模型知道它不是一次性事件
            if (count == MAX_OCCURRENCES) {
                sink.add(new Entry("全天", item.getText().trim() + "（高频重复）"));
            }
        } catch (Exception e) {
            // 非法 cron 的提醒直接跳过：日程清单是锦上添花，不能因为一条脏数据让整个生成失败
            sink.add(new Entry("--", item.getText().trim() + "（cron 表达式无法解析，已忽略时间）"));
        }
    }
}
