package dev.waiz.datamanager.repository;


import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.fileupload;


public interface FileUploadRepository extends JpaRepository<fileupload,UUID>{

}
