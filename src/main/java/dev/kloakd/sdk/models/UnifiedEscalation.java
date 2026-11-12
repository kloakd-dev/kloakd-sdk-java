package dev.kloakd.sdk.models;

/**
 * One step of a unified scrape's escalation (kobe-scraper
 * api/v1/endpoints/unified_scraping.py EscalationEvent).
 *
 * @param from      the level left
 * @param to        the level tried next
 * @param reason    why
 * @param timestamp ISO time
 */
public record UnifiedEscalation(String from, String to, String reason, String timestamp) {}
