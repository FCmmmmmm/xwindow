package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * “音乐动效”卡片：一条随播放状态起伏的伪实时波形动画（Fake Visualizer）。
 * <p>
 * 跨进程获取真实音频流成本过高，这里采用【预制数学曲线】方案：用多条叠加的正弦波
 * {@code y = A * sin(ωx + φ)} 绘制平滑的“动态波浪曲线”。默认持续播放、相位不断推进，
 * 多条不同频率/相位的波叠加成层次丰富的曲线；开启抗锯齿以保证曲线顺滑。
 */
public class MusicVisualizerPanel {
    /** 动画帧间隔（毫秒），约 30fps。 */
    private static final int FRAME_INTERVAL = 33;

    private WaveComponent wave;
    private JBLabel captionLabel;
    private Timer animationTimer;

    public JPanel GenerateMusicCard() {
        JPanel card = PanelGenerator.GenerateCardPanel();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        wave = new WaveComponent();
        wave.setAlignmentX(Component.CENTER_ALIGNMENT);

        captionLabel = new JBLabel("♪ 正在播放·随节奏起舞");
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        captionLabel.setForeground(JBColor.GRAY);
        captionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel content = new JBPanel<>();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(wave);
        content.add(Box.createVerticalStrut(8));
        content.add(captionLabel);

        card.add(content, BorderLayout.CENTER);

        startAnimation();
        return card;
    }

    /** 启动波形推进的动画定时器。 */
    private void startAnimation() {
        animationTimer = new Timer(FRAME_INTERVAL, e -> wave.tick());
        animationTimer.start();
    }

    /** 卡片被移除时停止定时器，避免泄漏。 */
    public void dispose() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /**
     * 自绘的波形动画组件：叠加多条不同角频率与相位速度的正弦波，
     * 相位持续推进，配合首尾收窄的包络与缓动振幅，形成层次丰富、平滑起伏的波形。
     */
    private static final class WaveComponent extends JBPanel<WaveComponent> {
        /** 叠加的正弦波层数。 */
        private static final int LAYERS = 5;
        /** 曲线水平采样步长（像素），越小越平滑。 */
        private static final int SAMPLE_STEP = 2;
        /** 振幅向目标值靠拢的缓动系数（0~1，越小越平滑）。 */
        private static final double AMPLITUDE_EASING = 0.06;
        /** 占组件半高的最大振幅比例。 */
        private static final double MAX_AMPLITUDE_RATIO = 0.66;
        /** 前景主波之外再叠一层半透明的“回声”波，增加纵深。 */
        private static final int ECHO_LAYERS = 2;

        /** 每层的角频率 ω、初相 φ、振幅权重、相位推进速度。 */
        private static final double[] OMEGA = {1.0, 2.0, 3.3, 5.1, 7.4};
        private static final double[] PHI0 = {0.0, 1.7, 3.4, 0.9, 2.6};
        private static final double[] WEIGHT = {1.0, 0.55, 0.34, 0.22, 0.14};
        private static final double[] SPEED = {0.9, -1.3, 1.7, -2.2, 2.9};

        /** 当前振幅系数（0=直线，1=满幅），逐帧向目标缓动。 */
        private double amplitude = 0.0;
        /** 累计推进的相位。 */
        private double phase = 0.0;

        WaveComponent() {
            setOpaque(false);
            Dimension size = new Dimension(260, 84);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        }

        /** 推进一帧：相位前进，振幅平滑逼近满幅。 */
        void tick() {
            amplitude += (1.0 - amplitude) * AMPLITUDE_EASING;
            phase += 0.05;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            double midY = h / 2.0;
            double maxAmp = midY * MAX_AMPLITUDE_RATIO * amplitude;

            // 中线基准
            g2.setColor(JBColor.namedColor("Separator.foreground", JBColor.LIGHT_GRAY));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, (int) Math.round(midY), w, (int) Math.round(midY));

            Color waveColor = new Color(90, 150, 245);

            // 先画若干条半透明“回声”波，相位略微错开，营造层叠纵深
            for (int echo = ECHO_LAYERS; echo >= 1; echo--) {
                double echoPhase = phase - echo * 0.55;
                double echoScale = 1.0 - echo * 0.16;
                int alpha = 60 / echo;
                g2.setColor(new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), alpha));
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(buildWave(w, midY, maxAmp * echoScale, echoPhase));
            }

            // 主波：不透明、较粗
            g2.setColor(waveColor);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(buildWave(w, midY, maxAmp, phase));

            g2.dispose();
        }

        /** 按当前相位构造一条叠加多层正弦的波形路径。 */
        private GeneralPath buildWave(int w, double midY, double maxAmp, double curPhase) {
            GeneralPath path = new GeneralPath();
            for (int x = 0; x <= w; x += SAMPLE_STEP) {
                double norm = (double) x / Math.max(1, w); // 0~1
                // 两端收窄的包络，让波形头尾自然收束
                double envelope = Math.sin(Math.PI * norm);

                double y = 0.0;
                for (int layer = 0; layer < LAYERS; layer++) {
                    double angle = OMEGA[layer] * (norm * 2 * Math.PI)
                            + PHI0[layer]
                            + curPhase * SPEED[layer];
                    y += WEIGHT[layer] * Math.sin(angle);
                }
                double py = midY - y * maxAmp * envelope;

                if (x == 0) {
                    path.moveTo(x, py);
                } else {
                    path.lineTo(x, py);
                }
            }
            return path;
        }
    }
}
