package com.jansetu.modules.application.repository;

import com.jansetu.modules.application.domain.CaseRecords.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
