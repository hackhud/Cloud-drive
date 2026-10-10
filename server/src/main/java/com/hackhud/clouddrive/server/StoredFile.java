package com.hackhud.clouddrive.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.hackhud.clouddrive.common.FileInfo;

import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "name"}))
public class StoredFile {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    private long size;
    private Instant createdAt;
    private Instant modifiedAt;
    private String uploadedBy;
    private String editedBy;

    protected StoredFile() {
    }

    public StoredFile(String owner, String name, long size, String user, Instant now) {
        this.owner = owner;
        this.name = name;
        this.size = size;
        this.createdAt = now;
        this.modifiedAt = now;
        this.uploadedBy = user;
        this.editedBy = user;
    }

    public void replaced(long newSize, String user, Instant now) {
        this.size = newSize;
        this.modifiedAt = now;
        this.editedBy = user;
    }

    public FileInfo toInfo() {
        return new FileInfo(id, name, size, createdAt, modifiedAt, uploadedBy, editedBy);
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }
}
