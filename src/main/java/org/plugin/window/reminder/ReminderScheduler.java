package org.plugin.window.reminder;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时提醒调度器（应用级服务）。
 * <p>为每条启用的提醒按 cron 计算下次触发时间并排程，触发后重新计算下一次（自我续期）。
 * 配置变更后调用 {@link #reload()} 重新排程。
 */
public final class ReminderScheduler {

    private static final Logger LOG = Logger.getInstance(ReminderScheduler.class);

    private final ScheduledExecutorService scheduler =
            AppExecutorUtil.getAppScheduledExecutorService();

    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    /** 排程代次：reload 后自增，使旧回调的续期失效，避免重复排程。 */
    private int generation = 0;

    public static ReminderScheduler getInstance() {
        return ApplicationManager.getApplication().getService(ReminderScheduler.class);
    }

    /** 取消现有排程并按最新配置重新排程。 */
    public synchronized void reload() {
        cancelAll();
        int gen = generation;
        for (ReminderItem item : ReminderSettings.getInstance().getReminders()) {
            if (item.isEnabled()) {
                scheduleNext(item, gen);
            }
        }
    }

    private synchronized void cancelAll() {
        generation++;
        for (ScheduledFuture<?> f : futures) {
            f.cancel(false);
        }
        futures.clear();
    }

    private synchronized void scheduleNext(ReminderItem item, int gen) {
        if (gen != generation) {
            return; // 已被新的 reload 取代
        }
        long delayMillis;
        try {
            Cron cron = CronSupport.parse(item.getCron());
            ZonedDateTime now = ZonedDateTime.now();
            Optional<ZonedDateTime> next = ExecutionTime.forCron(cron).nextExecution(now);
            if (next.isEmpty()) {
                return;
            }
            delayMillis = Math.max(0, Duration.between(now, next.get()).toMillis());
        } catch (Exception e) {
            LOG.warn("跳过非法 cron 提醒: " + item.getCron(), e);
            return;
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                ReminderNotifier.show(item.getText(), item.getType());
            } catch (Exception e) {
                LOG.warn("提醒展示失败", e);
            } finally {
                // 自我续期：排下一次
                scheduleNext(item, gen);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);

        futures.add(future);
    }
}
