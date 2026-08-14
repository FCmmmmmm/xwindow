package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import org.plugin.window.settings.UserProfileSettings;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;

/**
 * “今日搬砖进度”卡片：用极简像素线条展示上下班时段的推进程度，
 * 并附带距离下班/开工/周末的倒计时文案，给枯燥的业务代码增添一点盼头。
 * <p>
 * 内置三个彩蛋模式：
 * — 下班后敲码 → 超频搬砖（橙红进度条 + 正计时）
 * — 周末 → 圣人模式（全暗 + 距离下次受难计时）
 * — 下班前指定分钟 → 黎明前的黑暗（尾部格子闪烁亮绿）
 * <p>
 * 上下班时间、闪烁窗口、格子数量均由 {@link UserProfileSettings} 提供，可在设置页调整。
 */
public class WorkProgressPanel {

    private BarComponent bar;
    private JBLabel titleLabel;
    private UiFactory.GradientLabel percentLabel;
    private JBLabel countdownLabel;

    public JPanel GenerateWorkProgressCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        // 顶行：固定标题「今日专注」+ 右上角蓝紫渐变百分比
        titleLabel = new JBLabel();
        titleLabel.setForeground(JBColor.foreground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setText("🎯 今日专注");

        percentLabel = new UiFactory.GradientLabel("0%", SwingConstants.RIGHT);
        percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 15f));

        JPanel header = new JBPanel<>(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(percentLabel, BorderLayout.EAST);

        bar = new BarComponent();
        bar.setAlignmentX(Component.CENTER_ALIGNMENT);

        countdownLabel = new JBLabel();
        countdownLabel.setFont(countdownLabel.getFont().deriveFont(Font.PLAIN, 11f));
        countdownLabel.setForeground(JBColor.GRAY);
        countdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(header);
        content.add(Box.createVerticalStrut(10));
        content.add(bar);
        content.add(Box.createVerticalStrut(8));
        content.add(countdownLabel);

        card.add(content, BorderLayout.CENTER);

        refresh();
        return card;
    }

    /** 依据当前时刻刷新进度与文案，需在 EDT 调用（由每秒时间定时器驱动）。 */
    public void refresh() {
        if (bar == null) {
            return;
        }

        // 每次刷新都从设置里取一次，用户在设置页保存后无需重启即可生效
        UserProfileSettings settings = UserProfileSettings.getInstance();
        LocalTime start = UserProfileSettings.parseTimeOrDefault(settings.getWorkStartTime(), LocalTime.of(10, 0));
        LocalTime end = UserProfileSettings.parseTimeOrDefault(settings.getWorkEndTime(), LocalTime.of(19, 0));
        if (!end.isAfter(start)) {
            // 兜底：配置值非法时退回默认时段，避免出现负进度
            start = LocalTime.of(10, 0);
            end = LocalTime.of(19, 0);
        }
        int dawnMinutes = Math.max(0, settings.getDawnZoneMinutes());

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;

        LocalTime time = now.toLocalTime();

        String countdown;
        double progress;
        BarComponent.Mode mode;

        if (weekend) {
            // 圣人模式：把工作与生活在视觉上彻底切割
            countdown = "◷ 距离下次受难还有 " + formatDurationHours(Duration.between(now, nextWorkStart(now, start)));
            progress = 0.0;
            mode = BarComponent.Mode.SAINT;
        } else if (time.isBefore(start)) {
            countdown = "◷ 距离开工 " + formatDuration(Duration.between(time, start));
            progress = 0.0;
            mode = BarComponent.Mode.NORMAL;
        } else if (!time.isBefore(end)) {
            // 超频搬砖：过了下班点还在敲，进入"第二阶段 boss 战"
            countdown = "◷ 已自愿奉献 " + formatDuration(Duration.between(end, time));
            progress = 1.0;
            mode = BarComponent.Mode.OVERCLOCK;
        } else {
            long total = Duration.between(start, end).getSeconds();
            long passed = Duration.between(start, time).getSeconds();
            progress = Math.max(0.0, Math.min(1.0, (double) passed / total));
            countdown = "◷ 距离下班 " + formatDuration(Duration.between(time, end));
            // 下班前 dawnMinutes 分钟进入"黎明前的黑暗"，尾部格子闪烁提醒收尾
            boolean dawn = dawnMinutes > 0 && Duration.between(time, end).toMinutes() < dawnMinutes;
            mode = dawn ? BarComponent.Mode.DAWN : BarComponent.Mode.NORMAL;
        }

        titleLabel.setText("🎯 今日专注");
        percentLabel.setText(Math.round(progress * 100) + "%");
        countdownLabel.setText(countdown);
        bar.setProgress(progress, mode, settings.getProgressSegments());
    }

    /** 从给定时刻推算下一个工作日的开工点（用于周末圣人模式倒计时）。 */
    private static LocalDateTime nextWorkStart(LocalDateTime now, LocalTime startTime) {
        LocalDate date = now.toLocalDate();
        LocalDateTime candidate = date.atTime(startTime);
        // 若今日开工点已过或今日为周末，则顺延到下一个工作日
        while (!candidate.isAfter(now)
                || candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
            candidate = date.atTime(startTime);
        }
        return candidate;
    }

    private static String formatDuration(Duration d) {
        long total = Math.max(0, d.getSeconds());
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** 面向长跨度的"还有 X 小时 Y 分"文案，供周末倒计时使用。 */
    private static String formatDurationHours(Duration d) {
        long total = Math.max(0, d.getSeconds());
        long h = total / 3600;
        long m = (total % 3600) / 60;
        return h + " 小时 " + m + " 分";
    }

    /** 自绘的像素风进度条。 */
    private static final class BarComponent extends JBPanel<BarComponent> {
        /** 进度条的显示模式，决定配色与动画。 */
        enum Mode { NORMAL, OVERCLOCK, SAINT, DAWN }

        /** 黎明模式下尾部闪烁的格子数。 */
        private static final int DAWN_BLINK_CELLS = 2;
        /** 闪烁动画帧间隔（毫秒）。 */
        private static final int BLINK_INTERVAL = 500;
        /** 格子数的兜底下限，避免除零或除负。 */
        private static final int MIN_SEGMENTS = 1;

        private double progress = 0.0;
        private Mode mode = Mode.NORMAL;
        private int segments = 20;
        private final Timer blinkTimer;
        private boolean blinkOn = true;

        BarComponent() {
            setOpaque(false);
            Dimension size = new Dimension(220, 16);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

            // 独立的闪烁定时器，仅在黎明模式下驱动尾部格子明暗交替
            blinkTimer = new Timer(BLINK_INTERVAL, e -> {
                blinkOn = !blinkOn;
                if (mode == Mode.DAWN) {
                    repaint();
                }
            });
            blinkTimer.start();
        }

        void setProgress(double progress, Mode mode, int segments) {
            this.progress = progress;
            this.mode = mode;
            this.segments = Math.max(MIN_SEGMENTS, segments);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            int w = getWidth();
            int h = getHeight();
            int gap = 2;
            int segs = segments;
            int cell = (w - (segs - 1) * gap) / segs;
            if (cell < 1) {
                cell = 1;
            }
            int barW = cell * segs + (segs - 1) * gap;
            int x0 = (w - barW) / 2;
            int y = (h - cell) / 2;

            int filled = (int) Math.round(progress * segs);
            Color emptyColor = JBColor.namedColor("Separator.foreground", JBColor.LIGHT_GRAY);

            // 黎明模式下参与闪烁的格子起始下标（最后 DAWN_BLINK_CELLS 格）
            int blinkStart = segs - DAWN_BLINK_CELLS;
            Color dawnBlink = new Color(80, 220, 120);

            for (int i = 0; i < segs; i++) {
                int x = x0 + i * (cell + gap);
                Color color;
                if (mode == Mode.DAWN && i >= blinkStart && i < filled) {
                    // 尾部格子在亮绿与渐变填充色间交替，余光一瞥就知道该收尾了
                    color = blinkOn ? dawnBlink : UiFactory.gradientColor(i / (double) Math.max(1, segs - 1));
                } else if (i < filled) {
                    // 主态（NORMAL）用蓝紫逐格渐变，点缀克制不失辨识度
                    color = (mode == Mode.NORMAL)
                            ? UiFactory.gradientColor(i / (double) Math.max(1, segs - 1))
                            : fillColorForMode();
                } else {
                    color = emptyColor;
                }
                g2.setColor(color);
                g2.fillRect(x, y, cell, cell);
            }

            g2.dispose();
        }

        /** 依据当前模式返回已填充格子的颜色。 */
        private Color fillColorForMode() {
            switch (mode) {
                case OVERCLOCK:
                    // 第二阶段 boss 战：炽热的橙红
                    return new Color(240, 90, 40);
                case SAINT:
                    // 圣人模式：全暗，与背景近乎融为一体
                    return new Color(90, 95, 105);
                case DAWN:
                case NORMAL:
                default:
                    return new Color(90, 150, 245);
            }
        }
    }
}
