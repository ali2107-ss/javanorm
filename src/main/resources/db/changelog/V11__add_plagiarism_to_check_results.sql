ALTER TABLE check_results
ADD COLUMN uniqueness_percent INT,
ADD COLUMN plagiarism_result JSONB;
