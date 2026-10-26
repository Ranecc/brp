package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A chunk eating the server; OPEN until a human decides it isn't. */
@Entity
@Table(name = "chunk_incidents")
public class ChunkIncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String dimension;
    private String chunk;
    private double costScore;
    private String status = "OPEN";
    private Instant createdAt = Instant.now();

    protected ChunkIncidentEntity() {}

    public ChunkIncidentEntity(String tenantId, String dimension, String chunk, double costScore) {
        this.tenantId = tenantId;
        this.dimension = dimension;
        this.chunk = chunk;
        this.costScore = costScore;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getDimension() { return dimension; }
    public String getChunk() { return chunk; }
    public double getCostScore() { return costScore; }
    public String getStatus() { return status; }
}
