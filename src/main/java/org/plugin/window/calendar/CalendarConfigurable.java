package org.plugin.window.calendar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 设置页：Settings/Preferences → 工具箱日历。
 * <p>
 * 提供两条日程来源：macOS 系统日历（需一次自动化授权，可能被系统拒绝）与 ICS（纯文本，零权限）。
 * 「测试读取」按钮会优先测已填写的 ICS，其次测系统日历——因为授权状态在 IDE 里没有可读入口，
 * 用户被拒之后往往不知道该去哪改，得有一个能立刻验证并给出指引的地方。
 */
public class CalendarConfigurable implements Configurable {

    private JBCheckBox systemCalendarBox;
    private TextFieldWithBrowseButton icsField;
    private JBLabel statusLabel;
    private JButton testButton;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱日历";
    }

    @Override
    public @Nullable JComponent createComponent() {
        systemCalendarBox = new JBCheckBox("读取 macOS 系统日历（「今日指引」用它作为日程来源之一）");

        icsField = new TextFieldWithBrowseButton();
        icsField.addBrowseFolderListener("选择 ICS 文件", "也可以直接把 webcal/https 订阅链接粘贴到输入框",
                null, new FileChooserDescriptor(true, false, false, false, false, false));
        if (icsField.getTextField() instanceof JBTextField field) {
            field.getEmptyText().setText("例如 /Users/me/Downloads/work.ics 或 https://…/cal.ics");
        }

        JBLabel icsHint = new JBLabel("""
                <html>ICS 不需要任何系统权限：日历 App 里「文件 → 导出」，或在企业微信/Outlook 里导出 .ics，
                也可以填日历的订阅链接（webcal:// 会自动转成 https）。</html>""");
        icsHint.setForeground(JBColor.GRAY);

        JBLabel systemHint = new JBLabel("""
                <html>首次读取时系统会弹出「GoLand 想要控制“日历”」，点「好」即可。<br>
                若被系统直接拒绝（错误码 -10004），说明当前进程拿不到可授权身份，请改用下面的 ICS 来源。<br>
                未授权时插件不会报错，会自动退回使用 ICS 与「工具箱提醒」。</html>""");
        systemHint.setForeground(JBColor.GRAY);

        testButton = new JButton("测试读取今天的日程");
        testButton.addActionListener(e -> runTest());

        statusLabel = new JBLabel(" ");
        statusLabel.setForeground(JBColor.GRAY);

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        testRow.setOpaque(false);
        testRow.add(testButton);
        testRow.add(statusLabel);

        JPanel icsRow = new JPanel(new BorderLayout(0, 4));
        icsRow.setOpaque(false);
        icsRow.add(new JBLabel("ICS 文件或订阅链接："), BorderLayout.NORTH);
        icsRow.add(icsField, BorderLayout.CENTER);
        icsRow.add(icsHint, BorderLayout.SOUTH);
        icsRow.setBorder(JBUI.Borders.emptyTop(10));

        root = FormBuilder.createFormBuilder()
                .addComponent(systemCalendarBox)
                .addComponent(systemHint)
                .addComponent(icsRow)
                .addComponent(testRow)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        root.setBorder(JBUI.Borders.empty(4));

        reset();
        return root;
    }

    /** 后台线程真实读取一次；有 ICS 就先测 ICS（更可能是用户真正配好的来源）。 */
    private void runTest() {
        if (testButton == null || statusLabel == null) {
            return;
        }
        String icsSource = icsField == null ? "" : icsField.getText().trim();
        boolean useIcs = !icsSource.isBlank();

        testButton.setEnabled(false);
        statusLabel.setForeground(JBColor.GRAY);
        statusLabel.setText("正在读取…");

        AppExecutorUtil.getAppExecutorService().submit(() -> {
            String message;
            boolean ok;
            if (useIcs) {
                IcsCalendarApi.Result result = new IcsCalendarApi().todayEvents(icsSource);
                ok = result.isOk();
                message = ok
                        ? "ICS 读取成功，今天共 " + result.events().size() + " 条日程"
                        : result.error();
            } else {
                MacCalendarApi.Result result = new MacCalendarApi().todayEvents();
                ok = result.isOk();
                message = ok
                        ? (result.events().isEmpty() ? "系统日历已授权，今天没有日程"
                        : "系统日历已授权，今天共 " + result.events().size() + " 条日程")
                        : result.error();
            }
            ApplicationManager.getApplication().invokeLater(() -> {
                if (statusLabel == null) {
                    return;
                }
                testButton.setEnabled(true);
                statusLabel.setForeground(ok ? JBColor.GRAY : JBColor.RED);
                statusLabel.setText(message);
            });
        });
    }

    @Override
    public boolean isModified() {
        CalendarSettings s = CalendarSettings.getInstance();
        return systemCalendarBox.isSelected() != s.isSystemCalendarEnabled()
                || !icsField.getText().trim().equals(s.getIcsSource());
    }

    @Override
    public void apply() {
        CalendarSettings s = CalendarSettings.getInstance();
        s.setSystemCalendarEnabled(systemCalendarBox.isSelected());
        s.setIcsSource(icsField.getText());
    }

    @Override
    public void reset() {
        CalendarSettings s = CalendarSettings.getInstance();
        if (systemCalendarBox != null) {
            systemCalendarBox.setSelected(s.isSystemCalendarEnabled());
        }
        if (icsField != null) {
            icsField.setText(s.getIcsSource());
        }
        if (statusLabel != null) {
            statusLabel.setText(" ");
        }
    }

    @Override
    public void disposeUIResources() {
        systemCalendarBox = null;
        icsField = null;
        statusLabel = null;
        testButton = null;
        root = null;
    }
}
