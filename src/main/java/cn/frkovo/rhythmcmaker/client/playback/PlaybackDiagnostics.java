package cn.frkovo.rhythmcmaker.client.playback;

public final class PlaybackDiagnostics {
    private long poolHits;
    private long poolMisses;
    private long generatedEntities;
    private long recycledEntities;
    private long seekRebuilds;
    private long seekRebuildNanos;

    public void recordPoolHit() { poolHits++; }
    public void recordPoolMiss() { poolMisses++; }
    public void recordGeneratedEntity() { generatedEntities++; }
    public void recordRecycledEntity() { recycledEntities++; }
    public void recordSeekRebuild(long elapsedNanos) { seekRebuilds++; seekRebuildNanos += Math.max(0L, elapsedNanos); }
    public Snapshot snapshot() {
        return new Snapshot(poolHits, poolMisses, generatedEntities, recycledEntities, seekRebuilds, seekRebuildNanos);
    }

    public record Snapshot(long poolHits, long poolMisses, long generatedEntities,
                           long recycledEntities, long seekRebuilds, long seekRebuildNanos) {}
}