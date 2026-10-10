package com.hackhud.clouddrive.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByOwnerOrderByNameAsc(String owner);

    Optional<StoredFile> findByOwnerAndName(String owner, String name);

    Optional<StoredFile> findByIdAndOwner(Long id, String owner);
}
