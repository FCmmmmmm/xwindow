package org.plugin.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
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
    }
}