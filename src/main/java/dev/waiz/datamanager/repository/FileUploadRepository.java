package dev.waiz.datamanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.waiz.datamanager.model.fileupload;
import dev.waiz.datamanager.model.user;

public interface FileUploadRepository extends JpaRepository<fileupload,UUID>{

    List<fileupload> findByUser(user user);
}
