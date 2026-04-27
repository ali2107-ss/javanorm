package ru.normacontrol.infrastructure.parser;

/**
 * Перечисление поддерживаемых типов документов.
 *
 * @see DocumentParser
 * @see DocumentParserChain
 */
public enum DocumentType {
    /**
     * Microsoft Word формат (.docx).
     * <p>Поддерживается через Apache POI.</p>
     */
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),

    /**
     * Portable Document Format (.pdf).
     * <p>Поддерживается через Apache PDFBox.</p>
     */
    PDF("application/pdf", ".pdf"),

    /**
     * Простой текстовый файл (.txt).
     * <p>Поддерживается через стандартные Java методы.</p>
     */
    TXT("text/plain", ".txt"),

    /**
     * Markdown формат (.md).
     * <p>Поддерживается как простой текст с основными метаданными.</p>
     */
    MD("text/markdown", ".md");

    private final String mimeType;
    private final String extension;

    DocumentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    /**
     * Получить MIME-тип документа.
     *
     * @return MIME-тип (например, "application/pdf")
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Получить расширение файла.
     *
     * @return расширение с точкой (например, ".pdf")
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Определить тип документа по MIME-типу.
     *
     * @param mimeType MIME-тип файла
     * @return {@link DocumentType} или {@code null} если тип не поддерживается
     */
    public static DocumentType fromMimeType(String mimeType) {
        if (mimeType == null) return null;
        
        String lowerMime = mimeType.toLowerCase();
        for (DocumentType type : DocumentType.values()) {
            if (lowerMime.contains(type.mimeType.split("/")[1])) {
                return type;
            }
        }
        return null;
    }

    /**
     * Определить тип документа по расширению файла.
     *
     * @param filename имя файла (например, "document.pdf")
     * @return {@link DocumentType} или {@code null} если расширение не поддерживается
     */
    public static DocumentType fromFilename(String filename) {
        if (filename == null) return null;
        
        String lowerFilename = filename.toLowerCase();
        for (DocumentType type : DocumentType.values()) {
            if (lowerFilename.endsWith(type.extension)) {
                return type;
            }
        }
        return null;
    }
}
