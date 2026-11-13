package ca.sheridancollege.shtirthb.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ca.sheridancollege.shtirthb.model.LibraryResourceFile;

public interface LibraryResourceFileRepository
        extends JpaRepository<LibraryResourceFile, Long> {
}
