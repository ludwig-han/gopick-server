-- Run once before deployment if the notification table exists and this migration is pending.
-- If the table is absent, Hibernate creates the current schema at app startup.
-- Stop or replace older app instances before writing the new notification types.
-- VARCHAR preserves existing enum text and allows future notification types.
ALTER TABLE notification
    MODIFY COLUMN type VARCHAR(40) NOT NULL,
    ADD COLUMN actor_account_id BIGINT NULL;
