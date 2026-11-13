package ca.sheridancollege.shtirthb.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;

/*
 * The bytes of an uploaded Resource Library file. Kept in its own table so
 * that lists of resources never load file contents. The row is saved in the
 * same transaction as its LibraryResource.
 */
@Entity
@Table(name = "library_resource_files")
public class LibraryResourceFile {

    @Id
    @Column(name = "resource_id")
    private Long resourceId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] content;

    protected LibraryResourceFile() {
    }

    public LibraryResourceFile(Long resourceId, byte[] content) {
        this.resourceId = resourceId;
        this.content = content;
    }

    public Long getResourceId() { return resourceId; }
    public byte[] getContent() { return content; }
}
