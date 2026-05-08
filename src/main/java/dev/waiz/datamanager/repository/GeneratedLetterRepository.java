package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.generatedletter;

public interface GeneratedLetterRepository extends JpaRepository<generatedletter,UUID> {

    List<generatedletter> findByMapping_MappingId(UUID mappingId);
}
