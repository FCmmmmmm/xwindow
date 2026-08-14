package org.plugin.window.panel;

import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.time.LocalTime;
import java.util.Random;

public class PoemPanel {
    private static final String[] MORNING_POEMS = {
        "晨光熹微照窗台，\n鸟语花香入梦来。",
        "朝露未晞花正开，\n清风徐徐送香来。",
        "旭日东升照大地，\n万物苏醒展生机。"
    };

    private static final String[] NOON_POEMS = {
        "日正当中照四方，\n云淡风轻好时光。",
        "午后阳光暖洋洋，\n偷得浮生半日闲。",
        "烈日当空照，\n清风何处找。"
    };

    private static final String[] AFTERNOON_POEMS = {
        "夕阳西下晚霞红，\n归鸟投林影渐浓。",
        "斜阳照晚霞满天，\n一壶清茶品流年。",
        "黄昏时分景最美，\n落日余晖映心扉。"
    };

    private static final String[] EVENING_POEMS = {
        "月明星稀夜色凉，\n独坐窗前思故乡。",
        "夜深人静月当空，\n思绪万千入梦中。",
        "繁星点点夜未央，\n清风明月伴书香。"
    };

    private static final String[] MIDNIGHT_POEMS = {
        "夜深人静月光寒，\n独对孤灯思万千。",
        "三更半夜人未眠，\n思绪飘渺入云端。",
        "月落乌啼霜满天，\n夜深人静思无边。"
    };

    private JBLabel poemLabel;
    private final Random random = new Random();

    public JPanel GeneratePoemCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        // 左侧：氛围引号
        JBLabel quoteLabel = new JBLabel("“");
        quoteLabel.setFont(quoteLabel.getFont().deriveFont(Font.BOLD, 34f));
        quoteLabel.setForeground(UiFactory.PURPLE);

        // 中央：诗句（楷体）
        poemLabel = new JBLabel();
        poemLabel.setFont(resolveKaiTiFont());
        poemLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 右侧：紫色线稿山峰（纯代码绘制，无真实图片）
        MountainComponent mountain = new MountainComponent();

        card.add(quoteLabel, BorderLayout.WEST);
        card.add(poemLabel, BorderLayout.CENTER);
        card.add(mountain, BorderLayout.EAST);

        GenerateDisplayText(); // 初始加载诗句
        return card;
    }

    public void GenerateDisplayText() {
        if (this.poemLabel == null) {
            return;
        }

        String poem = generatePoemByTime();

        SwingUtilities.invokeLater(() -> {
            if (this.poemLabel != null) {
                this.poemLabel.setText("<html><div style='text-align: center;'>" +
                    poem.replace("\n", "<br>") + "</div></html>");
            }
        });
    }

    /** 解析楷体字体，兼容 macOS 的 STKaiti（与 UserPanel 一致）。 */
    private Font resolveKaiTiFont() {
        Font kaiTiFont = new Font("KaiTi", Font.PLAIN, 14);
        if (!kaiTiFont.getFamily().equals("KaiTi") && !kaiTiFont.getFamily().equals("楷体")) {
            kaiTiFont = new Font("STKaiti", Font.PLAIN, 14);
        }
        return kaiTiFont;
    }

    private String generatePoemByTime() {
        int hour = LocalTime.now().getHour();
        String[] poems;

        if (hour >= 5 && hour < 9) {
            poems = MORNING_POEMS;
        } else if (hour >= 9 && hour < 14) {
            poems = NOON_POEMS;
        } else if (hour >= 14 && hour < 18) {
            poems = AFTERNOON_POEMS;
        } else if (hour >= 18 && hour < 22) {
            poems = EVENING_POEMS;
        } else {
            poems = MIDNIGHT_POEMS;
        }

        return poems[random.nextInt(poems.length)];
    }

    /** 紫色线稿山峰装饰：主峰 + 前山 + 峰顶旗帜，纯 SVG 风格。 */
    private static final class MountainComponent extends JComponent {

        MountainComponent() {
            setPreferredSize(new Dimension(46, 42));
            setMinimumSize(new Dimension(46, 42));
            setMaximumSize(new Dimension(46, 42));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiFactory.PURPLE);
            g2.setStroke(new BasicStroke(1.6f));

            int w = getWidth();
            int h = getHeight();
            int baseY = h - 4;

            // 主峰（左低右高）
            Path2D main = new Path2D.Double();
            main.moveTo(2, baseY);
            main.lineTo(w * 0.42, 8);
            main.lineTo(w - 2, baseY);
            g2.draw(main);

            // 前山（较低，偏右）
            Path2D front = new Path2D.Double();
            front.moveTo(w * 0.3, baseY);
            front.lineTo(w * 0.62, 20);
            front.lineTo(w * 0.95, baseY);
            g2.draw(front);

            // 峰顶小旗帜
            double peakX = w * 0.42;
            double peakY = 8;
            g2.drawLine((int) peakX, (int) peakY, (int) peakX, (int) peakY - 9);
            g2.drawLine((int) peakX, (int) peakY - 9, (int) peakX + 11, (int) peakY - 6);

            g2.dispose();
        }
    }
}