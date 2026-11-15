-- Apply once to an existing MySQL database before deploying the #29 server.
-- Keep the existing foreign key and unique constraint; MySQL permits multiple NULLs.
ALTER TABLE project_member MODIFY COLUMN profile_id BIGINT NULL;
