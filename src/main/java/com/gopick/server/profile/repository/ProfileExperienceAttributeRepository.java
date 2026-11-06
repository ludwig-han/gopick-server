package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.ExperienceAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileExperienceAttributeRepository extends JpaRepository<ExperienceAttribute, Long> {
}
