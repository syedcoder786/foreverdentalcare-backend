package com.clinic.whatsapp;

/** Published when an admin starts a marketing campaign; sending begins after the transaction commits. */
public record CampaignStartedEvent(Long campaignId) {
}
