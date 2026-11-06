package cn.frkovo.rhythmcmaker.client;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;

record NoteTarget(ChartManifest chart, ChartManifest.Note note, Entity entity, HitResult hitResult) {
    boolean isDisplayEntity() {
        return entity != null;
    }
}