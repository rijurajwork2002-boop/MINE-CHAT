package com.minechat;

public class ChatEntryFactoryTest {
    public static void main(String[] args) {
        shouldTrimChatBodyAndSection();
        shouldFallbackUnknownSectionForSos();
    }

    private static void shouldTrimChatBodyAndSection() {
        ChatEntryFactory factory = new ChatEntryFactory(new ChatEntryFactory.TimeSource() {
            @Override
            public String currentLabel() {
                return "07:30";
            }
        });

        ChatEntry entry = factory.createChatMessage("  Need support at crusher line  ", "  East Drift 4  ");

        assert entry.getType() == ChatEntry.EntryType.CHAT;
        assert "Need support at crusher line".equals(entry.getBody());
        assert "East Drift 4".equals(entry.getSection());
        assert "07:30".equals(entry.getTimestampLabel());
    }

    private static void shouldFallbackUnknownSectionForSos() {
        ChatEntryFactory factory = new ChatEntryFactory(new ChatEntryFactory.TimeSource() {
            @Override
            public String currentLabel() {
                return "07:30";
            }
        });

        ChatEntry entry = factory.createSosMessage("   ");

        assert entry.getType() == ChatEntry.EntryType.SOS;
        assert "Unknown section".equals(entry.getSection());
        assert "SOS activated. Assistance needed immediately.".equals(entry.getBody());
    }
}
