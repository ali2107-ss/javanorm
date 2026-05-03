CREATE TABLE document_hashes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id),
  sentence_hash VARCHAR(64) NOT NULL,
  sentence_preview VARCHAR(100),
  sentence_index INT,
  created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_doc_hashes_hash ON document_hashes(sentence_hash);
CREATE INDEX idx_doc_hashes_doc ON document_hashes(document_id);
