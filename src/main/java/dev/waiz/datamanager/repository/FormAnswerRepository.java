package dev.waiz.datamanager.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.waiz.datamanager.model.formanswer;

public interface FormAnswerRepository extends JpaRepository<formanswer,UUID>{

}
