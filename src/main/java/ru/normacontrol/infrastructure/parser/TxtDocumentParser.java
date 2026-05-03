package ru.normacontrol.infrastructure.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class TxtDocumentParser implements DocumentParser {

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.TXT || type == DocumentType.MD;
  }

  @Override
  public ParsedDocument parse(InputStream stream) {
    try {
      String fullText = new String(stream.readAllBytes(), 
        StandardCharsets.UTF_8);
      
      List<ParsedSection> sections = new ArrayList<>();
      String[] lines = fullText.split("\n");
      
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i].trim();
        // Markdown заголовки
        if (line.startsWith("# ")) {
          sections.add(new ParsedSection(
            line.substring(2), 1, i));
        } else if (line.startsWith("## ")) {
          sections.add(new ParsedSection(
            line.substring(3), 2, i));
        }
        // Текстовые заголовки (цифра + точка)
        else if (line.matches("^\\d+\\.\\s+[А-ЯA-Z].*")) {
          sections.add(new ParsedSection(line, 1, i));
        }
      }
      
      return new ParsedDocument(fullText, sections,
        new ArrayList<>(), new ArrayList<>(), new HashMap<>());
        
    } catch (Exception e) {
      throw new RuntimeException("Ошибка чтения файла");
    }
  }
}
