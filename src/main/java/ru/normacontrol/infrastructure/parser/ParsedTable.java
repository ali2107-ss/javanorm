package ru.normacontrol.infrastructure.parser;

import java.util.List;

/**
 * Parsed document table.
 *
 * @param caption table caption
 * @param rows textual rows
 */
public record ParsedTable(String caption, List<String> rows) {
}
