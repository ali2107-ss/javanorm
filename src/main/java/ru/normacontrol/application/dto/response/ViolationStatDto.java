package ru.normacontrol.application.dto.response;

/**
 * DTO for top violation statistics in the admin dashboard.
 *
 * @param ruleCode rule code
 * @param count total occurrences
 * @param description representative description
 */
public record ViolationStatDto(String ruleCode, long count, String description) {
}
