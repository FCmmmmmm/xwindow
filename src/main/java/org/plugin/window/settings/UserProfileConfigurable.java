package org.plugin.window.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * 设置页：Settings/Preferences → 工具箱用户资料，配置头像、名称，以及“今日搬砖进度”卡片的时段/闪烁/格子参数。
 */
public class UserProfileConfigurable implements Configurable {

    private JBTextField nameField;
    private TextFieldWithBrowseButton avatarField;
    private JSpinner workStartSpinner;
    private JSpinner workEndSpinner;
    private JSpinner dawnZoneSpinner;
    private JSpinner segmentsSpinner;
    private JPanel root;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "工具箱用户资料";
    }

    @Override
    public @Nullable JComponent createComponent() {
        nameField = new JBTextField();
        avatarField = new TextFieldWithBrowseButton();
        avatarField.addBrowseFolderListener(
                "选择头像图片",
                "请选择一张本地图片作为头像",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor());

        JBLabel avatarHint = new JBLabel(
                "<html>头像支持 PNG/JPG 等本地图片，将自动缩放。留空则显示默认头像。</html>");
        avatarHint.setForeground(JBColor.GRAY);
        avatarHint.setBorder(JBUI.Borders.empty(4, 2, 8, 0));

        // 上下班时间使用 HH:mm 时间选择器，其余保留数字微调器
        workStartSpinner = buildTimeSpinner();
        workEndSpinner = buildTimeSpinner();
        dawnZoneSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 240, 5));
        segmentsSpinner = new JSpinner(new SpinnerNumberModel(20, 4, 60, 1));

        JBLabel workHint = new JBLabel(
                "<html>用于“今日搬砖进度”卡片。<br>"
                        + "· 上下班时间：24 小时制 HH:mm，下班时间需晚于上班时间。<br>"
                        + "· 闪烁提醒：下班前多少分钟起，进度条尾部格子开始闪烁。<br>"
                        + "· 格子数量：进度条切成多少段像素方块。</html>");
        workHint.setForeground(JBColor.GRAY);
        workHint.setBorder(JBUI.Borders.empty(4, 2, 0, 0));

        root = FormBuilder.createFormBuilder()
                .addLabeledComponent("名称：", nameField)
                .addLabeledComponent("头像图片：", avatarField)
                .addComponent(avatarHint)
                .addComponent(new TitledSeparator("今日搬砖进度"))
                .addLabeledComponent("上班时间：", workStartSpinner)
                .addLabeledComponent("下班时间：", workEndSpinner)
                .addLabeledComponent("下班前闪烁提醒（分钟）：", dawnZoneSpinner)
                .addLabeledComponent("进度条格子数量：", segmentsSpinner)
                .addComponent(workHint)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        reset();
        return root;
    }

    /** 构造一个仅显示 HH:mm 的时间微调器，箭头以分钟为步长。 */
    private static JSpinner buildTimeSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(dateAt(0, 0), null, null, Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        return spinner;
    }

    /** 用给定时分构造一个当天的 {@link Date}，供 SpinnerDateModel 使用（其他字段清零）。 */
    private static Date dateAt(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static Date dateAt(LocalTime time) {
        return dateAt(time.getHour(), time.getMinute());
    }

    /** 从 spinner 里取出当前值并格式化为 HH:mm。 */
    private static String readTime(JSpinner spinner) {
        Date value = (Date) spinner.getValue();
        Calendar c = Calendar.getInstance();
        c.setTime(value);
        return String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    @Override
    public boolean isModified() {
        UserProfileSettings s = UserProfileSettings.getInstance();
        return !Objects.equals(nameField.getText().trim(), s.getUserName())
                || !Objects.equals(avatarField.getText().trim(), s.getAvatarPath())
                || !Objects.equals(readTime(workStartSpinner), s.getWorkStartTime())
                || !Objects.equals(readTime(workEndSpinner), s.getWorkEndTime())
                || (int) dawnZoneSpinner.getValue() != s.getDawnZoneMinutes()
                || (int) segmentsSpinner.getValue() != s.getProgressSegments();
    }

    @Override
    public void apply() throws ConfigurationException {
        String start = readTime(workStartSpinner);
        String end = readTime(workEndSpinner);
        LocalTime startTime = LocalTime.parse(start, UserProfileSettings.TIME_FMT);
        LocalTime endTime = LocalTime.parse(end, UserProfileSettings.TIME_FMT);
        if (!endTime.isAfter(startTime)) {
            throw new ConfigurationException("下班时间必须晚于上班时间。");
        }

        UserProfileSettings s = UserProfileSettings.getInstance();
        s.setUserName(nameField.getText().trim());
        s.setAvatarPath(avatarField.getText().trim());
        s.setWorkStartTime(start);
        s.setWorkEndTime(end);
        s.setDawnZoneMinutes((int) dawnZoneSpinner.getValue());
        s.setProgressSegments((int) segmentsSpinner.getValue());
    }

    @Override
    public void reset() {
        UserProfileSettings s = UserProfileSettings.getInstance();
        nameField.setText(s.getUserName());
        avatarField.setText(s.getAvatarPath());
        // 加载时对非法的时间字符串做兜底：解析失败退回默认值
        LocalTime start = UserProfileSettings.parseTimeOrDefault(s.getWorkStartTime(), LocalTime.of(10, 0));
        LocalTime end = UserProfileSettings.parseTimeOrDefault(s.getWorkEndTime(), LocalTime.of(19, 0));
        workStartSpinner.setValue(dateAt(start));
        workEndSpinner.setValue(dateAt(end));
        dawnZoneSpinner.setValue(s.getDawnZoneMinutes());
        segmentsSpinner.setValue(s.getProgressSegments());
    }

    @Override
    public void disposeUIResources() {
        nameField = null;
        avatarField = null;
        workStartSpinner = null;
        workEndSpinner = null;
        dawnZoneSpinner = null;
        segmentsSpinner = null;
        root = null;
    }
}
