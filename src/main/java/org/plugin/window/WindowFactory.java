package org.plugin.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

public class WindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull com.intellij.openapi.wm.ToolWindow toolWindow) {
        // 实例化我们自己写的 UI 界面
        MainWindow myToolWindow = new MainWindow();

        // 获取 ContentFactory
        ContentFactory contentFactory = ContentFactory.getInstance();

        // 创建 Content（不设置 tab 名称，使用 ToolWindow 的标题）
        Content content = contentFactory.createContent(myToolWindow.getContent(), "", false);

        // 将 Content 添加到 Tool Window 中
        toolWindow.getContentManager().addContent(content);

        // 内容被移除（窗口关闭/IDE 退出）时注销实例，避免设置页重建已关闭的窗口
        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void contentRemoved(@NotNull ContentManagerEvent event) {
                if (event.getContent() == content) {
                    MainWindow.unregister(myToolWindow);
                }
            }
        });
    }
}
