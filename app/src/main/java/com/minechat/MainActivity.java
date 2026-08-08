package com.minechat;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final ChatEntryFactory entryFactory = new ChatEntryFactory();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView statusTextView = findViewById(R.id.statusTextView);
        final EditText sectionEditText = findViewById(R.id.sectionEditText);
        final EditText messageEditText = findViewById(R.id.messageEditText);
        final Button sendButton = findViewById(R.id.sendButton);
        final Button sosButton = findViewById(R.id.sosButton);
        final LinearLayout logContainer = findViewById(R.id.logContainer);

        addEntryView(logContainer, entryFactory.createChatMessage(
                getString(R.string.default_log_message),
                getString(R.string.default_section)
        ));

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = messageEditText.getText().toString();
                if (message.trim().isEmpty()) {
                    messageEditText.setError(getString(R.string.message_required));
                    return;
                }

                addEntryView(logContainer, entryFactory.createChatMessage(
                        message,
                        sectionEditText.getText().toString()
                ));
                statusTextView.setText(R.string.offline_status_ready);
                messageEditText.setText("");
            }
        });

        sosButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addEntryView(logContainer, entryFactory.createSosMessage(sectionEditText.getText().toString()));
                statusTextView.setText(R.string.offline_status_sos);
            }
        });
    }

    private void addEntryView(LinearLayout logContainer, ChatEntry entry) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_chat_entry, logContainer, false);
        TextView badgeView = itemView.findViewById(R.id.badgeTextView);
        TextView bodyView = itemView.findViewById(R.id.bodyTextView);
        TextView metaView = itemView.findViewById(R.id.metaTextView);

        boolean isSos = entry.getType() == ChatEntry.EntryType.SOS;
        badgeView.setText(isSos ? R.string.sos_badge : R.string.chat_badge);
        badgeView.setBackgroundColor(Color.parseColor(isSos ? "#B3261E" : "#6C8D4A"));
        bodyView.setText(entry.getBody());
        metaView.setText(getString(
                R.string.chat_meta,
                entry.getSender(),
                entry.getSection(),
                entry.getTimestampLabel()
        ));

        logContainer.addView(itemView, 0);
    }
}
