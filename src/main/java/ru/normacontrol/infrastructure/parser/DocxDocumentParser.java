package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.POIXMLProperties.CoreProperties;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DocxDocumentParser implements DocumentParser {

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.DOCX;
  }

  @Override
  public ParsedDocument parse(InputStream stream) {
    try {
      XWPFDocument doc = new XWPFDocument(stream);
      
      StringBuilder fullText = new StringBuilder();
      List<ParsedSection> sections = new ArrayList<>();
      List<ParsedTable> tables = new ArrayList<>();
      List<String> figureCaptions = new ArrayList<>();
      
      // Читаем все параграфы
      for (XWPFParagraph para : doc.getParagraphs()) {
        String text = para.getText().trim();
        if (text.isEmpty()) continue;
        
        fullText.append(text).append("\n");
        
        // Определяем заголовки
        String style = para.getStyle();
        if (style != null && style.startsWith("Heading")) {
          int level = 1;
          try {
            level = Integer.parseInt(style.replace("Heading","").trim());
          } catch(Exception ignored){}
          sections.add(new ParsedSection(
            text, 
            level,
            fullText.length()
          ));
        }
        
        // Ищем подписи рисунков
        if (text.toLowerCase().startsWith("рисунок") ||
            text.toLowerCase().startsWith("рис.")) {
          figureCaptions.add(text);
        }
      }
      
      // Читаем таблицы
      for (XWPFTable table : doc.getTables()) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
          List<String> cells = new ArrayList<>();
          for (XWPFTableCell cell : row.getTableCells()) {
            cells.add(cell.getText().trim());
          }
          rows.add(cells);
        }
        tables.add(new ParsedTable(rows));
      }
      
      // Метаданные
      Map<String, String> metadata = new HashMap<>();
      CoreProperties props = doc.getProperties().getCoreProperties();
      if (props.getCreator() != null) 
        metadata.put("author", props.getCreator());
      if (props.getTitle() != null)   
        metadata.put("title", props.getTitle());
      
      doc.close();
      
      return new ParsedDocument(
        fullText.toString(), sections, tables, 
        figureCaptions, metadata);
        
    } catch (Exception e) {
      log.error("Ошибка парсинга DOCX: {}", e.getMessage());
      throw new RuntimeException("Не удалось разобрать DOCX файл");
    }
  }
}
