package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One UTC minute of server load (docs/13 §2). mspt_avg is an incremental mean
 * over the minute's heartbeat samples; everything else is a maximum — a
 * one-second spike is load, averages forgive too much.
 */
@Entity
@Table(name = "load_samples")
public class LoadSampleEntity {

    @Id
    @Column(name = "bucket_minute")
    private LocalDateTime bucketMinute;

    private int sampleCount;
    private double msptAvg;
    private double msptMax;
    private int playerMax;
    private int entityMax;
    private int chunkMax;
    private int forcedMax;

    protected LoadSampleEntity() {}

    public LoadSampleEntity(LocalDateTime bucketMinute, double mspt,
                            int players, int entities, int chunks, int forced) {
        this.bucketMinute = bucketMinute;
        this.sampleCount = 1;
        this.msptAvg = mspt;
        this.msptMax = mspt;
        this.playerMax = players;
        this.entityMax = entities;
        this.chunkMax = chunks;
        this.forcedMax = forced;
    }

    public LocalDateTime getBucketMinute() { return bucketMinute; }
    public int getSampleCount() { return sampleCount; }
    public double getMsptAvg() { return msptAvg; }
    public double getMsptMax() { return msptMax; }
    public int getPlayerMax() { return playerMax; }
    public int getEntityMax() { return entityMax; }
    public int getChunkMax() { return chunkMax; }
    public int getForcedMax() { return forcedMax; }

    /** Incremental mean: the row-locked caller serializes concurrent samples. */
    public void addSample(double mspt, int players, int entities, int chunks, int forced) {
        this.msptAvg = (this.msptAvg * this.sampleCount + mspt) / (this.sampleCount + 1);
        this.sampleCount++;
        this.msptMax = Math.max(this.msptMax, mspt);
        this.playerMax = Math.max(this.playerMax, players);
        this.entityMax = Math.max(this.entityMax, entities);
        this.chunkMax = Math.max(this.chunkMax, chunks);
        this.forcedMax = Math.max(this.forcedMax, forced);
    }
}
