package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;

/**
 * “音乐动效·频谱”卡片：一排像素风频谱柱（Fake Visualizer 的折线柱状版）。
 * <p>
 * 采用【预制数学曲线】方案，不解析真实音频：每根柱子的瞬时能量由多条不同频率的正弦波
 * 叠加得到 {@code h = Σ Aᵢ·sin(ωᵢt + φᵢ)}，再经【非线性指数拉伸】放大动态范围 → 高低起伏更剧烈、更有敲击感。
 * 柱身遵循“快起跳 + 指数衰减”的包络（Attack/Decay），柱顶叠一顶带【重力加速下落】的“落帽”（Falling Peak Cap），
 * 强化打击节奏感；开启抗锯齿保证边缘顺滑。整体律动强度还会与时段/天气数据做轻量联动。
 */
public class MusicSpectrumPanel {
    /** 动画帧间隔（毫秒），约 30fps。 */
    private static final int FRAME_INTERVAL = 33;

    private SpectrumComponent spectrum;
    private JBLabel captionLabel;
    private Timer animationTimer;

    public JPanel GenerateMusicCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        spectrum = new SpectrumComponent();
        spectrum.setAlignmentX(Component.CENTER_ALIGNMENT);

        captionLabel = new JBLabel("♪ 正在播放·节奏跳动中");
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        captionLabel.setForeground(JBColor.GRAY);
        captionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(spectrum);
        content.add(Box.createVerticalStrut(8));
        content.add(captionLabel);

        card.add(content, BorderLayout.CENTER);

        // 首帧即按当前时段设定一次律动强度
        RefreshLinkage();
        startAnimation();
        return card;
    }

    /** 启动频谱跳动的动画定时器。 */
    private void startAnimation() {
        animationTimer = new Timer(FRAME_INTERVAL, e -> spectrum.tick());
        animationTimer.start();
    }

    /**
     * 与时段/天气数据的轻量联动：仅按当前时段刷新“律动强度”，不新增定时器、无网络开销，
     * 由 {@code MainWindow} 复用现有秒级/半时级刷新回调触发即可，保持低能耗。
     */
    public void RefreshLinkage() {
        if (spectrum != null) {
            spectrum.setEnergy(energyOfHour(LocalTime.now().getHour()));
        }
    }

    /** 时段律动强度曲线：深夜偏静、白天清醒、傍晚最躁动。返回 0.55~1.0。 */
    private static double energyOfHour(int hour) {
        if (hour >= 0 && hour < 6) return 0.55;   // 深夜：安静低幅
        if (hour < 11) return 0.8;                // 上午：逐步唤醒
        if (hour < 18) return 0.9;                // 下午：稳定活跃
        if (hour < 23) return 1.0;                // 傍晚/夜间：最躁动
        return 0.7;                               // 临近午夜：回落
    }

    /** 卡片被移除时停止定时器，避免泄漏。 */
    public void dispose() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /**
     * 自绘的频谱柱组件：每根柱子的瞬时能量由多层正弦叠加、经非线性指数拉伸算得，
     * 柱身按“快起跳 + 指数衰减”包络运动，柱顶另有一顶随重力加速下落的“落帽”。
     */
    private static final class SpectrumComponent extends JBPanel<SpectrumComponent> {
        /** 频谱柱数量（减少根数、加粗柱体以增强层次）。 */
        private static final int BARS = 18;
        /** 柱间空隙（像素）。 */
        private static final int GAP = 4;
        /** 柱身起跳的追赶系数：目标高于当前时快速蹿升，营造敲击感。 */
        private static final double ATTACK = 0.6;
        /** 柱身回落的指数衰减系数（每帧乘以该值，<1 即指数衰减）。 */
        private static final double DECAY = 0.86;
        /** 非线性指数拉伸的 gamma（>1 压低谷、拔高峰，增大动态范围）。 */
        private static final double GAMMA = 2.2;
        /** 落帽重力加速度（每帧对下落速度的增量，像素/帧²，归一化空间）。 */
        private static final double PEAK_GRAVITY = 0.0035;
        /** 落帽厚度（像素）。 */
        private static final int PEAK_THICKNESS = 3;
        /** 落帽被顶起后的悬停帧数（短暂停顿再下落，更接近经典 VU 表）。 */
        private static final int PEAK_HANG_FRAMES = 6;
        /** 柱高占组件高度的最大比例。 */
        private static final double MAX_HEIGHT_RATIO = 0.92;

        /** 叠加正弦的层数与各层角频率、相位速度。 */
        private static final double[] OMEGA = {1.0, 2.7, 4.3};
        private static final double[] SPEED = {1.3, -1.9, 2.5};

        /** 每根柱子的当前高度比例、落帽位置比例（0~1）。 */
        private final double[] levels = new double[BARS];
        private final double[] peaks = new double[BARS];
        /** 每顶落帽的当前下落速度、以及顶起后剩余的悬停帧数。 */
        private final double[] peakVel = new double[BARS];
        private final int[] peakHang = new int[BARS];
        /** 累计推进的相位。 */
        private double phase = 0.0;
        /** 律动强度（0.55~1.0），由时段/天气联动设定，缩放整体振幅与相位步进。 */
        private double energy = 0.9;

        SpectrumComponent() {
            setOpaque(false);
            Dimension size = new Dimension(260, 88);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        }

        /** 设定律动强度（线程安全的简单赋值，越大越剧烈）。 */
        void setEnergy(double e) {
            energy = Math.max(0.4, Math.min(1.0, e));
        }

        /** 推进一帧：重算各柱瞬时能量，按“快起跳+指数衰减”更新柱身，落帽随重力加速下落。 */
        void tick() {
            // 强度越高相位推进越快，节奏更密
            phase += 0.12 * (0.6 + 0.4 * energy);
            for (int i = 0; i < BARS; i++) {
                double target = targetLevel(i);

                // 柱身包络：目标更高 → 快速起跳；否则 → 指数衰减回落
                if (target > levels[i]) {
                    levels[i] += (target - levels[i]) * ATTACK;
                } else {
                    levels[i] *= DECAY;
                    if (levels[i] < target) levels[i] = target;
                }

                // 落帽：被柱身顶起则复位并悬停，随后按重力加速下落
                if (levels[i] >= peaks[i]) {
                    peaks[i] = levels[i];
                    peakVel[i] = 0.0;
                    peakHang[i] = PEAK_HANG_FRAMES;
                } else if (peakHang[i] > 0) {
                    peakHang[i]--;
                } else {
                    peakVel[i] += PEAK_GRAVITY;      // 重力持续加速
                    peaks[i] -= peakVel[i];
                    if (peaks[i] < levels[i]) {      // 落回柱顶后与柱身贴合
                        peaks[i] = levels[i];
                        peakVel[i] = 0.0;
                    }
                }
            }
            repaint();
        }

        /**
         * 第 i 根柱子的瞬时能量（0~1）：多层正弦叠加 + 钟形频响包络后，
         * 再经 gamma 指数拉伸放大动态范围，让强弱对比更剧烈、更有敲击感。
         */
        private double targetLevel(int i) {
            double norm = (double) i / (BARS - 1); // 0~1，作为频段位置
            double sum = 0.0;
            double weight = 0.0;
            for (int layer = 0; layer < OMEGA.length; layer++) {
                double angle = OMEGA[layer] * (norm * 2 * Math.PI + i * 0.5)
                        + phase * SPEED[layer];
                sum += Math.sin(angle);
                weight += 1.0;
            }
            // 归一化到 0~1，再叠一层中间高、两端低的“钟形”包络，模拟常见频响
            double base = (sum / weight + 1.0) / 2.0;
            double envelope = 0.35 + 0.65 * Math.sin(Math.PI * norm);
            double linear = base * envelope;
            // 非线性指数拉伸：gamma>1 → 谷更低、峰更高，动态范围显著加大
            double shaped = Math.pow(linear, GAMMA) * energy;
            return Math.max(0.04, Math.min(1.0, shaped));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int totalGap = (BARS - 1) * GAP;
            int barW = Math.max(2, (w - totalGap) / BARS);
            int usedW = barW * BARS + totalGap;
            int x0 = (w - usedW) / 2;
            int maxH = (int) (h * MAX_HEIGHT_RATIO);
            int baseY = h; // 柱子从底部向上生长

            Color low = new Color(90, 150, 245);
            Color high = new Color(150, 90, 245);

            for (int i = 0; i < BARS; i++) {
                int x = x0 + i * (barW + GAP);

                int barH = Math.max(2, (int) Math.round(levels[i] * maxH));
                int y = baseY - barH;

                // 柱身：底蓝顶紫的竖向渐变，越高越偏紫
                Color top = blend(low, high, levels[i]);
                g2.setPaint(new GradientPaint(x, baseY, low, x, y, top));
                g2.fillRoundRect(x, y, barW, barH, 4, 4);

                // 落帽：漂浮在柱子上方、随重力加速下落的一小段亮条
                int peakH = (int) Math.round(peaks[i] * maxH);
                int peakY = baseY - peakH - PEAK_THICKNESS;
                g2.setColor(new Color(220, 230, 255));
                g2.fillRoundRect(x, Math.max(0, peakY), barW, PEAK_THICKNESS, 3, 3);
            }

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
