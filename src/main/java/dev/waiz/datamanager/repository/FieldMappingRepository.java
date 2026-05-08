package dev.waiz.datamanager.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import dev.waiz.datamanager.model.fieldmapping;

public interface FieldMappingRepository extends JpaRepository<fieldmapping, UUID> {

    List<fieldmapping> findByOcr_OcrId(UUID ocrId);

    List<fieldmapping> findByTemplate_TemplateId(UUID templateId);
}


