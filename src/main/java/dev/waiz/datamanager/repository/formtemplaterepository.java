package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.formtemplate;

public interface formtemplaterepository extends JpaRepository<formtemplate, UUID> {

    List<formtemplate>findByIsActiveTrue();
}
