package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 独立时钟卡片：居中显示大号时间 + 日期（星期用蓝紫渐变）。
 * <p>
 * 单独成卡，不再与头像/名称挤占宽度；窗口过窄时字号按可用宽度自动缩小，保证时间完整显示。
 * 由 {@code MainWindow} 每秒调用 {@link #refreshClock()} 更新。
 */
public class ClockPanel {

    private static final String TIME_FORMAT = "HH:mm:ss";
    /** 时钟基准字号（px）。窗口过窄时按可用宽度自动缩小。 */
    private static final int TIME_BASE_SIZE = 32;

    private JPanel card;
    private JBLabel timeLabel;
    private JBLabel dateLabel;
    private UiFactory.GradientLabel weekdayLabel;

    public JPanel GenerateClockCard() {
        this.card = PanelGenerator.GenerateCardPanel();
        JPanel card = this.card;
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        card.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitTimeFont();
            }
        });

        timeLabel = new JBLabel();
        timeLabel.setForeground(JBColor.foreground());
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, TIME_BASE_SIZE));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        dateLabel = new JBLabel();
        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.PLAIN, 13f));
        dateLabel.setForeground(JBColor.GRAY);

        weekdayLabel = new UiFactory.GradientLabel("", SwingConstants.LEFT);
        weekdayLabel.setFont(weekdayLabel.getFont().deriveFont(Font.BOLD, 13f));

        JPanel dateRow = new JBPanel<>();
        dateRow.setLayout(new BoxLayout(dateRow, BoxLayout.X_AXIS));
        dateRow.setOpaque(false);
        dateRow.add(dateLabel);
        dateRow.add(Box.createHorizontalStrut(8));
        dateRow.add(weekdayLabel);
        dateRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(timeLabel);
        content.add(Box.createVerticalStrut(2));
        content.add(dateRow);

        // 用 GridBagLayout 让整块内容在卡片内居中
        JPanel wrapper = new JBPanel<>(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(content);

        card.add(wrapper, BorderLayout.CENTER);

        refreshClock();
        return card;
    }

    /** 刷新时钟与日期（需在 EDT 调用，由 MainWindow 的每秒定时器驱动）。 */
    public void refreshClock() {
        if (timeLabel == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        timeLabel.setText(now.format(DateTimeFormatter.ofPattern(TIME_FORMAT)));
        dateLabel.setText(now.getMonthValue() + "月" + now.getDayOfMonth() + "日");
        weekdayLabel.setText("星期" + weekName(now.getDayOfWeek().getValue()));
    }

    /** 依据卡片可用宽度动态调整时钟字号，保证窄窗口下时间也能完整显示。 */
    private void fitTimeFont() {
        if (timeLabel == null || card == null) {
            return;
        }
        int avail = card.getWidth() - 60; // 60 = 卡片左右内边距(18*2) + 余量
        int size = TIME_BASE_SIZE;
        while (size > 16) {
            Font f = timeLabel.getFont().deriveFont(Font.BOLD, size);
            if (timeLabel.getFontMetrics(f).stringWidth("00:00:00") <= avail) {
                timeLabel.setFont(f);
                return;
            }
            size -= 2;
        }
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, size));
    }

    /** 数字 1–7 对应的中文星期名。 */
    private static String weekName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            default -> "日";
        };
    }
}