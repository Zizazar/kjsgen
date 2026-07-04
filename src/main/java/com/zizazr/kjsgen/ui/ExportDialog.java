package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.RecipeValidator;
import com.zizazr.kjsgen.integration.kubejs.KubeJsExporter;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Export options + summary. Writing goes through {@link KubeJsExporter}
 * (marker-block merge inside kubejs/server_scripts).
 */
public final class ExportDialog {
    private ExportDialog() {
    }

    public static void show(KjsGenUI ui) {
        RecipeProject project = ui.project();

        Dialog dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.export_title").getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(240));

        UIElement content = new UIElement();
        content.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(3));

        // summary: recipes per target file
        Map<String, List<RecipeInstance>> byFile = project.recipesByTargetFile();
        if (byFile.isEmpty()) {
            content.addChild(smallLabel(Component.translatable("kjsgen.ui.export_empty"), 0xffff5555));
        }
        byFile.forEach((file, recipes) -> content.addChild(smallLabel(
                Component.literal("server_scripts/" + KubeJsExporter.sanitizeFileName(file) + ".js — "
                        + recipes.size()), 0xffffffff)));

        // default file name
        UIElement fileRow = new UIElement();
        fileRow.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label fileLabel = new Label();
        fileLabel.setText("kjsgen.ui.default_file");
        fileLabel.textStyle(style -> style.fontSize(8));
        TextField fileField = new TextField();
        fileField.setText(project.defaultTargetFile(), false);
        fileField.layout(layout -> layout.flex(1).height(12));
        fileField.setTextResponder(project::setDefaultTargetFile);
        fileRow.addChildren(fileLabel, fileField);
        content.addChild(fileRow);

        // comments toggle
        Toggle comments = new Toggle();
        comments.setText("kjsgen.ui.export_comments");
        comments.setOn(project.exportComments());
        comments.setOnToggleChanged(project::setExportComments);
        content.addChild(comments);

        // warnings
        if (!KjsGen.isKubeJsLoaded()) {
            content.addChild(smallLabel(Component.translatable("kjsgen.export.warn_no_kubejs"), 0xffffcc55));
        }
        long invalid = project.recipes().stream()
                .filter(recipe -> RecipeTypeRegistry.get(recipe.typeId())
                        .map(type -> RecipeValidator.hasErrors(RecipeValidator.validate(recipe, type)))
                        .orElse(true))
                .count();
        if (invalid > 0) {
            content.addChild(smallLabel(Component.translatable("kjsgen.ui.export_invalid", invalid), 0xffff5555));
        }

        dialog.addContent(content);

        dialog.addButton(new Button()
                .setText("kjsgen.ui.preview_code")
                .setOnClick(e -> CodePreviewDialog.show(ui)));
        dialog.addButton(new Button()
                .setText("kjsgen.ui.export")
                .setOnClick(e -> {
                    ui.saveProject(false);
                    try {
                        KubeJsExporter.ExportResult result = KubeJsExporter.exportProject(project);
                        dialog.close();
                        StringBuilder info = new StringBuilder();
                        for (KubeJsExporter.FileResult file : result.files()) {
                            info.append(file.fileName()).append(".js (").append(file.recipeCount()).append(")\n");
                        }
                        Dialog.showNotification(
                                Component.translatable("kjsgen.ui.export_done").getString(),
                                info.toString(), null).show(ui.root());
                    } catch (IOException ex) {
                        KjsGen.LOGGER.error("kjsgen export failed", ex);
                        Dialog.showNotification(
                                Component.translatable("kjsgen.error.export_failed").getString(),
                                String.valueOf(ex.getMessage()), null).show(ui.root());
                    }
                }));
        dialog.addButton(new Button().setText("kjsgen.ui.cancel").setOnClick(e -> dialog.close()));
        dialog.show(ui.root());
    }

    private static Label smallLabel(Component text, int color) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> layout.widthPercent(100));
        label.textStyle(style -> style.fontSize(7).textColor(color).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        return label;
    }
}
