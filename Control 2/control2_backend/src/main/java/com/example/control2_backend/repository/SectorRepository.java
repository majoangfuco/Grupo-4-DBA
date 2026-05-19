package com.example.control2_backend.repository;

import com.example.control2_backend.entity.SectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SectorRepository extends JpaRepository<SectorEntity, Long> {
    Optional<SectorEntity> findByNombre(String nombre);
}
