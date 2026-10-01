package io.wispforest.owo.ui.component;

import cn.frkovo.rhythmcmaker.client.ImGuiExtensionRuntime;
import imgui.ImVec2;
import imgui.extension.implot.ImPlot;

public final class PlotComponent extends UiComponent {
    private final String title;
    private final String series;
    private final float[] values;
    private final double xStart;
    private final double xEnd;

    public PlotComponent(String title, String series, float[] values, double xStart, double xEnd) {
        this.title = title;
        this.series = series;
        this.values = values == null ? new float[0] : values;
        this.xStart = xStart;
        this.xEnd = xEnd;
    }

    @Override public void render() {
        if (!ImGuiExtensionRuntime.imPlotReady() || values.length == 0) return;
        if (ImPlot.beginPlot(title, "Beat", series, new ImVec2(-1.0f, 180.0f))) {
            ImPlot.setNextPlotLimits(xStart, xEnd, 0.0, 1.0, 1);
            Float[] xValues = new Float[values.length];
            Float[] yValues = new Float[values.length];
            double step = values.length <= 1 ? 0.0 : (xEnd - xStart) / (values.length - 1);
            for (int index = 0; index < values.length; index++) {
                xValues[index] = (float) (xStart + step * index);
                yValues[index] = values[index];
            }
            ImPlot.plotLine(series, xValues, yValues);
            ImPlot.endPlot();
        }
    }
}