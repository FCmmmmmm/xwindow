package org.plugin.window.panel;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * 共享 UI 原语：蓝紫渐变配色 + 圆角卡片 + 渐变文本/按钮 + 圆形头像。
 * <p>
 * 设计令牌来自 {@code resources/demand_img} 设计稿：深色主题、圆角 10–14px 卡片、
 * 淡灰描边、蓝紫渐变点缀。所有卡片、重点数据、刷新按钮都复用这里的原语，
 * 保证整窗观感统一。
 */
public final class UiFactory {

    /** 渐变起点：蓝色。 */
    public static final Color BLUE = new Color(0x4C8DFF);
    /** 渐变终点：紫色。 */
    public static final Color PURPLE = new Color(0x9D6BFF);

    /**
     * 卡片背景。
     * {@code JBColor(亮色主题色, 暗色主题色)}：亮色主题用近白，暗色主题用比 IDE 面板背景略亮的深灰。
     */
    public static final JBColor CARD_BG =
            new JBColor(new Color(0xF5F6F8), new Color(0x2A2D33));

    /** 卡片圆角半径（px）。 */
    public static final int CARD_ARC = 12;

    /** 卡片描边色：比默认 {@code JBColor.border()} 略深一档，让卡片边缘清晰但不抢眼。 */
    public static final JBColor CARD_BORDER =
            new JBColor(new Color(0xD4D7DC), new Color(0x555A63));

    private UiFactory() {
    }

    /** 返回蓝→紫渐变在 t∈[0,1] 处的插值色，用于分段进度条逐格渐变。 */
    public static Color gradientColor(double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (BLUE.getRed() + (PURPLE.getRed() - BLUE.getRed()) * t);
        int g = (int) (BLUE.getGreen() + (PURPLE.getGreen() - BLUE.getGreen()) * t);
        int b = (int) (BLUE.getBlue() + (PURPLE.getBlue() - BLUE.getBlue()) * t);
        return new Color(r, g, b);
    }

    /**
     * 用蓝紫垂直渐变绘制文本的标签，用于「周几」「专注百分比」「余额」等重点数据。
     * <p>
     * 失败态可调用 {@link #setGradientEnabled(boolean)} 回退成普通前景色（灰）。
     */
    public static class GradientLabel extends JBLabel {

        private boolean gradientEnabled = true;

        public GradientLabel() {
        }

        public GradientLabel(String text, int horizontalAlignment) {
            super(text, horizontalAlignment);
        }

        public void setGradientEnabled(boolean gradientEnabled) {
            this.gradientEnabled = gradientEnabled;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String text = getText();
            if (text == null || text.isEmpty()) {
                g2.dispose();
                return;
            }

            FontMetrics fm = g2.getFontMetrics(getFont());
            int textWidth = fm.stringWidth(text);
            int x;
            switch (getHorizontalAlignment()) {
                case SwingConstants.CENTER:
                    x = (getWidth() - textWidth) / 2;
                    break;
                case SwingConstants.RIGHT:
                    x = getWidth() - textWidth - getInsets().right;
                    break;
                default:
                    x = getInsets().left;
                    break;
            }
            int y = getHeight() / 2 + (fm.getAscent() - fm.getDescent()) / 2;

            if (gradientEnabled) {
                float h = Math.max(getHeight(), 1);
                g2.setPaint(new LinearGradientPaint(0, 0, 0, h,
                        new float[]{0f, 1f}, new Color[]{BLUE, PURPLE}));
            } else {
                g2.setPaint(getForeground());
            }
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    /** 圆角 + 淡灰描边的边框，供所有卡片复用。 */
    public static class RoundedBorder extends AbstractBorder {

        private final int arc;
        private final Color color;
        private final int thickness;

        public RoundedBorder(int arc, Color color, int thickness) {
            this.arc = arc;
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x + thickness / 2, y + thickness / 2,
                    w - thickness, h - thickness, arc, arc);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            int pad = arc / 2 + 2;
            return new Insets(pad, pad, pad, pad);
        }
    }

    /** 蓝紫渐变背景、圆角、hover 提亮的按钮，用于底部「手动刷新」。 */
    public static class GradientButton extends JButton {

        private boolean hover;

        public GradientButton(String text) {
            super(text);
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(Color.WHITE);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c1 = hover ? BLUE.brighter() : BLUE;
            Color c2 = hover ? PURPLE.brighter() : PURPLE;
            float h = Math.max(getHeight(), 1);
            g2.setPaint(new LinearGradientPaint(0, 0, 0, h, new float[]{0f, 1f}, new Color[]{c1, c2}));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
 * 生成默认头像：蓝紫渐变圆底 + 白色首字母，用于未配置头像时保证始终可见。
 * <p>
 * 不依赖 emoji 字体（沙箱环境 emoji 常渲染成空白），用 Java2D 绘制，任何主题下都稳定显示。
 */
    public static Icon defaultAvatarIcon(String initial, int size) {
        BufferedImage buf = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new LinearGradientPaint(0, 0, 0, size, new float[]{0f, 1f}, new Color[]{BLUE, PURPLE}));
        g2.fill(new Ellipse2D.Float(0, 0, size, size));

        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, size * 0.5f));
        FontMetrics fm = g2.getFontMetrics();
        String letter = (initial == null || initial.isEmpty()) ? "?" : initial.substring(0, 1).toUpperCase();
        g2.drawString(letter, (size - fm.stringWidth(letter)) / 2f,
                (size + (fm.getAscent() - fm.getDescent())) / 2f);

        g2.dispose();
        return new ImageIcon(buf);
    }

    /** 把图片缩放并裁剪成指定边长的圆形 {@link Icon}，用于头像。 */
    public static Icon circularIcon(BufferedImage image, int size) {
        BufferedImage buf = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setClip(new Ellipse2D.Float(0, 0, size, size));
        g2.drawImage(image, 0, 0, size, size, null);
        g2.dispose();
        return new ImageIcon(buf);
    }

    /**
     * 生成「圆形头像 + 右下角蓝紫音乐徽标」的组合图标，用于顶部名片卡。
     * <p>
     * 头像带一圈淡紫描边，右下角叠一个蓝紫渐变圆底 + 白色音符，呼应设计稿的「耳机/音乐图标」。
     */
    public static Icon musicAvatarIcon(BufferedImage image, int size) {
        int badge = Math.max(18, size / 3);
        int canvas = size + badge / 2;
        BufferedImage buf = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 圆形头像（同步缩放绘制，避免未加载完渲染成黑块）
        g2.setClip(new Ellipse2D.Float(0, 0, size, size));
        g2.drawImage(image, 0, 0, size, size, null);
        g2.setClip(null);

        // 头像淡紫描边
        g2.setColor(new Color(PURPLE.getRed(), PURPLE.getGreen(), PURPLE.getBlue(), 170));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Float(0.75f, 0.75f, size - 1.5f, size - 1.5f));

        // 右下角音乐徽标：蓝紫渐变圆底 + 白色音符
        int cx = size - 2;
        int cy = size - 2;
        int r = badge / 2;
        g2.setPaint(new LinearGradientPaint(cx - r, cy - r, cx + r, cy + r,
                new float[]{0f, 1f}, new Color[]{BLUE, PURPLE}));
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, badge, badge));

        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, badge * 0.72f));
        FontMetrics fm = g2.getFontMetrics();
        String note = "♪";
        g2.drawString(note, cx - fm.stringWidth(note) / 2f,
                cy + (fm.getAscent() - fm.getDescent()) / 2f);

        g2.dispose();
        return new ImageIcon(buf);
    }
}