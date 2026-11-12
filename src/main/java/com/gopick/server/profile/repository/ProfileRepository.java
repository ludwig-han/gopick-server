package com.gopick.server.profile.repository;

import com.gopick.server.profile.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    boolean existsByAccount_Id(Long accountId);
    Optional<Profile> findByAccount_Id(Long accountId);

    @Query("""
            select p from Profile p
            where p.isPublic = true
              and (:technology is null or exists (
                  select 1 from ProfileTechnology t
                  where t.profile = p and lower(trim(t.technology)) = :technology
              ))
              and (:interest is null or exists (
                  select 1 from ProfileInterest i
                  where i.profile = p and lower(trim(i.field)) = :interest
              ))
              and (:preferredRole is null or exists (
                  select 1 from ProfileRole r
                  where r.profile = p and lower(trim(r.role)) = :preferredRole
              ))
            order by p.id desc
            """)
    List<Profile> searchPublicProfiles(@Param("technology") String technology,
                                       @Param("interest") String interest,
                                       @Param("preferredRole") String preferredRole);
}
