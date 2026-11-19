-- Apply before deploying the server when Hibernate cannot create tables automatically.
-- Existing profile.contact_platform and profile.contact_id remain the first contact;
-- no data copy or destructive change is needed. New rows store contacts 2 and 3.
CREATE TABLE IF NOT EXISTS profile_additional_contact (
    profile_id BIGINT NOT NULL,
    contact_order INT NOT NULL,
    platform VARCHAR(255) NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (profile_id, contact_order),
    CONSTRAINT fk_profile_additional_contact_profile FOREIGN KEY (profile_id) REFERENCES profile (id)
);
