package org.plugin.window.reminder;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

/**
 * 提醒展示封装：根据配置以气泡通知或模态弹窗呈现。
 */
public final class ReminderNotifier {

    /** 与 plugin.xml 中 notificationGroup id 保持一致 */
    private static final String GROUP_ID = "工具箱提醒";
    private static final String TITLE = "工具箱提醒";

    private ReminderNotifier() {
    }

    public static void show(String text, ReminderItem.NotifyType type) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (type == ReminderItem.NotifyType.DIALOG) {
            showDialog(text);
        } else {
            showBalloon(text);
        }
    }

    private static void showBalloon(String text) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(TITLE, text, NotificationType.INFORMATION)
                .notify(null);
    }

    private static void showDialog(String text) {
        // 模态弹窗必须在 EDT 上调用
        ApplicationManager.getApplication().invokeLater(
                () -> Messages.showInfoMessage(text, TITLE)
        );
    }
}
