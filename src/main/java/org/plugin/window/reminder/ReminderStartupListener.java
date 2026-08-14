package org.plugin.window.reminder;

import com.intellij.ide.AppLifecycleListener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * IDE 启动后启动提醒排程。
 */
public class ReminderStartupListener implements AppLifecycleListener {

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        ReminderScheduler.getInstance().reload();
    }
}
