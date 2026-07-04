package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.integration.kubejs.ScriptAssembler;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shows the full generated KubeJS code, grouped per target file, before it is
 * written to disk.
 */
public final class CodePreviewDialog {
    private CodePreviewDialog() {
    }

    public static void show(KjsGenUI ui) {
        RecipeProject project = ui.project();

        Dialog dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.preview_code").getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(300));

        ScrollerView scroller = new ScrollerView();
        scroller.layout(layout -> layout.widthPercent(100).height(170));
        scroller.viewContainer(view -> view.getLayout().gapAll(3).flexDirection(FlexDirection.COLUMN));

        project.recipesByTargetFile().forEach((file, recipes) -> {
            Label header = new Label();
            header.setText("// " + file + ".js", false);
            header.textStyle(style -> style.fontSize(8).textColor(0xff55ff55));
            scroller.addScrollViewChild(header);

            String code = ScriptAssembler.assemble(project, recipes);
            TextArea area = new TextArea();
            area.setLines(List.of(code.split("\n", -1)));
            area.textAreaStyle(style -> style.fontSize(7));
            area.layout(layout -> layout.widthPercent(100).heightAuto());
            scroller.addScrollViewChild(area);
        });

        UIElement content = new UIElement();
        content.layout(layout -> layout.widthPercent(100));
        content.addChild(scroller);
        dialog.addContent(content);
        dialog.addButton(new Button().setText("kjsgen.ui.close").setOnClick(e -> dialog.close()));
        dialog.show(ui.root());
    }
}
