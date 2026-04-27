package ru.normacontrol.infrastructure.parser;

/**
 * Parsed document section.
 *
 * @param title section title
 * @param content section content
 */
public record ParsedSection(String title, String content) {
}
