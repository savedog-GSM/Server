package com.example.savedog.Owner.repository;

import com.example.savedog.Owner.domain.OwnerFace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OwnerFaceRepository extends JpaRepository<OwnerFace, Long> {
}
