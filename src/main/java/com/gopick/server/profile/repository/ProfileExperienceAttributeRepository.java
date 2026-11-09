package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ExperienceAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProfileExperienceAttributeRepository extends JpaRepository<ExperienceAttribute, Long> {
    List<ExperienceAttribute> findAllByExperience_IdInOrderByIdAsc(Collection<Long> experienceIds);
}
