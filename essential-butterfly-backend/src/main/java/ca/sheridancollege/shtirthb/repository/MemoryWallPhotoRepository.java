package ca.sheridancollege.shtirthb.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ca.sheridancollege.shtirthb.model.MemoryWallPhoto;

public interface MemoryWallPhotoRepository
        extends JpaRepository<MemoryWallPhoto, Long> {

    /*
     * Serialises the changes that count photos (adding, publishing) so two
     * administrators cannot both slip past a limit. The lock is released when
     * the transaction ends.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:key)) l",
            nativeQuery = true)
    long lockForChanges(@Param("key") long key);

    // Everything, newest upload first (the administrator's view).
    @Query("""
            select p
              from MemoryWallPhoto p
              left join fetch p.testimonial
             order by p.uploadedAt desc, p.id desc
            """)
    List<MemoryWallPhoto> findAllForAdmin();

    @Query("""
            select p
              from MemoryWallPhoto p
              left join fetch p.testimonial
             where p.id = :id
            """)
    Optional<MemoryWallPhoto> findWithTestimonial(@Param("id") Long id);

    @Query("""
            select p
              from MemoryWallPhoto p
              left join fetch p.testimonial
             where p.id in :ids
            """)
    List<MemoryWallPhoto> findAllWithTestimonialByIdIn(
            @Param("ids") Collection<Long> ids);

    /*
     * What a visitor may see: published photos only. A photo that came with a
     * testimonial is also hidden as soon as that testimonial is no longer
     * approved.
     */
    @EntityGraph(attributePaths = "testimonial")
    @Query("""
            select p
              from MemoryWallPhoto p
              left join p.testimonial t
             where p.status = 'PUBLISHED'
               and (p.source = 'ADMIN_UPLOAD'
                    or (t.status = 'APPROVED' and t.publicationConsent = true))
             order by p.uploadedAt desc, p.id desc
            """)
    List<MemoryWallPhoto> findAllPublic();

    @EntityGraph(attributePaths = "testimonial")
    @Query("""
            select p
              from MemoryWallPhoto p
              left join p.testimonial t
             where p.id = :id
               and p.status = 'PUBLISHED'
               and (p.source = 'ADMIN_UPLOAD'
                    or (t.status = 'APPROVED' and t.publicationConsent = true))
            """)
    Optional<MemoryWallPhoto> findPublicById(@Param("id") Long id);

    long countByStatus(String status);

    @Query("select coalesce(sum(p.sizeBytes), 0) from MemoryWallPhoto p")
    long totalSizeBytes();
}
