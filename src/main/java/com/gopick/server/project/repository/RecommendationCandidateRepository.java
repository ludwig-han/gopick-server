package com.gopick.server.project.repository;

import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationCandidateRepository extends Repository<Profile, Long> {
    @Query("""
            select count(m) > 0 from ProjectMember m
            where m.project.id = :projectId and m.profile.account.id = :accountId and m.memberType = LEADER
            """)
    boolean hasLeader(@Param("projectId") Long projectId, @Param("accountId") Long accountId);

    @Query("""
            select p.id from Profile p
            where p.isPublic = true and (:beforeId is null or p.id < :beforeId)
              and not exists (select 1 from ProjectMember m where m.project.id = :projectId and m.profile = p)
            order by p.id desc
            """)
    List<Long> findPublicNonMemberIds(@Param("projectId") Long projectId, @Param("beforeId") Long beforeId,
                                    Pageable page);

    List<Profile> findByIdInOrderByIdDesc(List<Long> ids);

    @Query("""
            select t.profile.id as profileId, t.technology as name, t.level as level
            from ProfileTechnology t where t.profile.id in :profileIds
            """)
    List<TechnologyEvidence> findTechnologyEvidence(@Param("profileIds") List<Long> profileIds);

    @Query("""
            select e.profile.id as profileId, e.id as experienceId, a.category as category, a.value as attributeValue
            from ProfileExperience e
            left join ExperienceAttribute a on a.experience = e and a.category in (TYPE, FIELD)
            where e.profile.id in :profileIds
            """)
    List<ExperienceEvidence> findExperienceEvidence(@Param("profileIds") List<Long> profileIds);

    interface TechnologyEvidence {
        Long getProfileId();
        String getName();
        int getLevel();
    }

    interface ExperienceEvidence {
        Long getProfileId();
        Long getExperienceId();
        ExperienceAttributeCategory getCategory();
        String getAttributeValue();
    }
}
