package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeProject;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

/**
 * Open / create / delete saved projects.
 */
public final class ProjectsDialog {
    private ProjectsDialog() {
    }

    public static void show(KjsGenUI ui) {
        Dialog dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.projects").getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(200));

        ScrollerView list = new ScrollerView();
        list.layout(layout -> layout.widthPercent(100).height(120));
        list.viewContainer(view -> view.getLayout().gapAll(1));

        Runnable refresh = getRefresher(ui, dialog, list);
        refresh.run();

        UIElement content = new UIElement();
        content.layout(layout -> layout.widthPercent(100));
        content.addChild(list);
        dialog.addContent(content);

        dialog.addButton(new Button()
                .setText("kjsgen.ui.new_project")
                .setOnClick(e -> Dialog.stringEditorDialog(
                        Component.translatable("kjsgen.ui.new_project").getString(),
                        "my_project",
                        name -> !name.isBlank(),
                        name -> {
                            dialog.close();
                            ui.setProject(new RecipeProject(name.trim()));
                        }).darkenBackground().show(ui.root())));
        dialog.addButton(new Button().setText("kjsgen.ui.close").setOnClick(e -> dialog.close()));
        dialog.show(ui.root());
    }

    private static Runnable getRefresher(KjsGenUI ui, Dialog dialog, ScrollerView list) {
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.viewContainer(UIElement::clearAllChildren);
            for (String name : ProjectManager.listProjects()) {
                UIElement row = new UIElement();
                row.layout(layout -> layout
                        .flexDirection(FlexDirection.ROW)
                        .widthPercent(100)
                        .gapAll(1));

                Button open = new Button();
                open.setText(name, false);
                open.layout(layout -> layout.flex(1));
                open.setOnClick(e -> ProjectManager.load(name).ifPresent(project -> {
                    dialog.close();
                    ui.setProject(project);
                }));

                Button delete = new Button();
                delete.noText();
                delete.layout(layout -> layout.width(14).height(14));
                delete.addPreIcon(Icons.REMOVE);
                delete.style(style -> style.tooltips(Component.translatable("kjsgen.ui.delete")));
                delete.setOnClick(e -> {
                    ProjectManager.delete(name);
                    refresh[0].run();
                });

                row.addChildren(open, delete);
                list.addScrollViewChild(row);
            }
        };
        return refresh[0];
    }
}
