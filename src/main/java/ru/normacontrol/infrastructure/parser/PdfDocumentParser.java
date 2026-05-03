package ru.normacontrol.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component  
public class PdfDocumentParser implements DocumentParser {

  @Override
  public boolean supports(DocumentType type) {
    return type == DocumentType.PDF;
  }

  @Override
  public ParsedDocument parse(InputStream stream) {
    try {
      PDDocument pdf = Loader.loadPDF(stream.readAllBytes());
      PDFTextStripper stripper = new PDFTextStripper();
      String fullText = stripper.getText(pdf);
      
      // Разбиваем на секции по заголовкам
      List<ParsedSection> sections = new ArrayList<>();
      String[] lines = fullText.split("\n");
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i].trim();
        // Заголовок если короткий и заглавными буквами
        if (line.length() > 3 && line.length() < 80 &&
            line.equals(line.toUpperCase()) &&
            !line.matches(".*\\d{2}.*")) {
          sections.add(new ParsedSection(line, 1, i));
        }
      }
      
      // Подписи рисунков
      List<String> figureCaptions = Arrays.stream(lines)
        .filter(l -> l.trim().toLowerCase().startsWith("рисунок")
                  || l.trim().toLowerCase().startsWith("рис."))
        .collect(Collectors.toList());
      
      pdf.close();
      
      return new ParsedDocument(fullText, sections, 
        new ArrayList<>(), figureCaptions, new HashMap<>());
        
    } catch (Exception e) {
      log.error("Ошибка парсинга PDF: {}", e.getMessage());
      throw new RuntimeException("Не удалось разобрать PDF файл");
    }
  }
}
