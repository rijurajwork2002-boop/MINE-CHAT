package com.minechat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatEntryFactory {
    interface TimeSource {
        String currentLabel();
    }

    private static class DeviceTimeSource implements TimeSource {
        private final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm", Locale.US);

        @Override
        public String currentLabel() {
            return formatter.format(new Date());
        }
    }

    private final TimeSource timeSource;

    public ChatEntryFactory() {
        this(new DeviceTimeSource());
    }

    ChatEntryFactory(TimeSource timeSource) {
        this.timeSource = timeSource;
    }

    public ChatEntry createChatMessage(String message, String section) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be blank.");
        }

        return new ChatEntry(
                ChatEntry.EntryType.CHAT,
                "Miner",
                normalizedMessage,
                sanitizeSection(section),
                timeSource.currentLabel()
        );
    }

    public ChatEntry createSosMessage(String section) {
        return new ChatEntry(
                ChatEntry.EntryType.SOS,
                "Emergency beacon",
                "SOS activated. Assistance needed immediately.",
                sanitizeSection(section),
                timeSource.currentLabel()
        );
    }

    private String sanitizeSection(String section) {
        String normalizedSection = section == null ? "" : section.trim();
        return normalizedSection.isEmpty() ? "Unknown section" : normalizedSection;
    }
}
