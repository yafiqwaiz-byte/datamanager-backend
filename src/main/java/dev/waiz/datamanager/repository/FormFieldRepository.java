package dev.waiz.datamanager.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.waiz.datamanager.model.formfield;

public interface FormFieldRepository extends JpaRepository<formfield,UUID>{

}
