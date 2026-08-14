package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

/**
 * “赛博篝火”卡片：底部一簇像素风小火苗，随编码节奏起舞。
 * <p>
 * 每敲击键盘火苗就添一把柴（heat 上升、烧得更旺），空闲时火苗缓缓变弱；
 * 一个独立的动画定时器让火焰持续摇曳。热度是纯粹的视觉燃料，不追踪任何输入内容。
 */
public class FirePanel {
    /** 每次按键增加的热度。 */
    private static final double HEAT_PER_KEY = 0.06;
    /** 每帧自然衰减的热度。 */
    private static final double HEAT_DECAY = 0.006;
    /** 动画帧间隔（毫秒）。 */
    private static final int FRAME_INTERVAL = 90;

    private FlameComponent flame;
    private JBLabel captionLabel;
    private Timer animationTimer;
    private AWTEventListener keyListener;

    public JPanel GenerateFireCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        flame = new FlameComponent();
        flame.setAlignmentX(Component.CENTER_ALIGNMENT);

        captionLabel = new JBLabel("篝火·敲键添柴");
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        captionLabel.setForeground(JBColor.GRAY);
        captionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(flame);
        content.add(Box.createVerticalStrut(6));
        content.add(captionLabel);

        card.add(content, BorderLayout.CENTER);

        startAnimation();
        registerKeyListener();
        return card;
    }

    /** 启动火焰摇曳的动画定时器。 */
    private void startAnimation() {
        animationTimer = new Timer(FRAME_INTERVAL, e -> {
            flame.tick(HEAT_DECAY);
            updateCaption();
        });
        animationTimer.start();
    }

    /** 注册全局按键监听：每次敲击给火苗添柴。 */
    private void registerKeyListener() {
        keyListener = event -> {
            if (event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED) {
                flame.addHeat(HEAT_PER_KEY);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(keyListener, AWTEvent.KEY_EVENT_MASK);
    }

    private void updateCaption() {
        double heat = flame.getHeat();
        String text;
        if (heat > 0.75) {
            text = "🔥 熊熊燃烧·手感火热";
        } else if (heat > 0.4) {
            text = "🔥 噼啪作响·渐入佳境";
        } else if (heat > 0.12) {
            text = "篝火·敲键添柴";
        } else {
            text = "余烬微光·歇会儿也行";
        }
        captionLabel.setText(text);
    }

    /** 卡片被移除时释放全局监听与定时器，避免泄漏。 */
    public void dispose() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (keyListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(keyListener);
        }
    }

    /** 自绘的像素火焰。热度越高，火苗越高越亮，摇曳越剧烈。 */
    private static final class FlameComponent extends JBPanel<FlameComponent> {
        /** 像素格边长。 */
        private static final int PX = 5;
        /** 火焰网格宽（格）。 */
        private static final int COLS = 7;
        /** 火焰网格高（格）。 */
        private static final int ROWS = 11;

        private double heat = 0.25;
        private long frame = 0;

        FlameComponent() {
            setOpaque(false);
            Dimension size = new Dimension(COLS * PX + 20, ROWS * PX + 10);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        }

        void addHeat(double delta) {
            heat = Math.min(1.0, heat + delta);
            repaint();
        }

        void tick(double decay) {
            heat = Math.max(0.0, heat - decay);
            frame++;
            repaint();
        }

        double getHeat() {
            return heat;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            int w = getWidth();
            int h = getHeight();
            int gridW = COLS * PX;
            int gridH = ROWS * PX;
            int originX = (w - gridW) / 2;
            int originY = h - gridH - 2;

            // 火焰高度随热度增长（至少 3 格，最多 ROWS 格）
            int flameHeight = 3 + (int) Math.round(heat * (ROWS - 3));

            // 用简单的正弦摇曳偏移，热度越高摆动越明显
            double sway = Math.sin(frame * 0.4) * (0.5 + heat * 1.5);

            Color core = blend(new Color(255, 240, 120), new Color(255, 255, 220), heat);
            Color mid = blend(new Color(255, 150, 30), new Color(255, 90, 20), heat);
            Color outer = new Color(200, 60, 15);

            for (int row = 0; row < flameHeight; row++) {
                // row 从底部往上：0 为最底部
                int yLevel = ROWS - 1 - row;
                double t = (double) row / Math.max(1, flameHeight - 1); // 0 底 -> 1 顶

                // 越往上越窄
                int halfWidth = (int) Math.round((COLS / 2.0) * (1.0 - t) + 0.2);
                // 摇曳只作用于上半部
                int offset = (int) Math.round(sway * t);

                for (int c = -halfWidth; c <= halfWidth; c++) {
                    int col = COLS / 2 + c + offset;
                    if (col < 0 || col >= COLS) {
                        continue;
                    }
                    // 顶端有概率缺格，形成跳动的火尖
                    if (t > 0.7 && ((frame + row + col) % 3 == 0)) {
                        continue;
                    }

                    Color color;
                    if (t < 0.35) {
                        color = mid;
                    } else if (t < 0.7) {
                        color = blend(mid, outer, (t - 0.35) / 0.35);
                    } else {
                        color = outer;
                    }
                    // 中心一竖列为高亮火芯
                    if (c == 0 && t < 0.6) {
                        color = core;
                    }

                    int x = originX + col * PX;
                    int y = originY + yLevel * PX;
                    g2.setColor(color);
                    g2.fillRect(x, y, PX - 1, PX - 1);
                }
            }

            // 底部柴堆（两根深色像素木条）
            g2.setColor(new Color(110, 70, 45));
            int logY = originY + (ROWS - 1) * PX + 1;
            g2.fillRect(originX + PX, logY, gridW - 2 * PX, PX - 1);

            g2.dispose();
        }

        private static Color blend(Color a, Color b, double t) {
            t = Math.max(0, Math.min(1, t));
            int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(r, g, bl);
        }
    }
}
