package com.minechat.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.minechat.app.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends BaseAdapter {
    private final Context context;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(Context context) {
        this.context = context;
    }

    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        notifyDataSetChanged();
    }

    public void setMessages(List<ChatMessage> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return messages.size(); }
    @Override public Object getItem(int i) { return messages.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatMessage msg = messages.get(position);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(8, 4, 8, 4);

        if (msg.isSos()) {
            row.setBackgroundColor(Color.parseColor("#B71C1C"));
        }

        if (!msg.isLocal()) {
            TextView sender = new TextView(context);
            sender.setText(msg.getSenderName());
            sender.setTextColor(msg.isSos() ? Color.WHITE : Color.parseColor("#FFD700"));
            sender.setTextSize(11f);
            row.addView(sender);
        }

        TextView textView = new TextView(context);
        textView.setText(msg.getText());
        textView.setTextSize(15f);
        if (msg.isSos()) {
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(16f);
        } else if (msg.isLocal()) {
            textView.setTextColor(Color.BLACK);
            row.setBackgroundColor(Color.parseColor("#FFD700"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.END;
            row.setLayoutParams(lp);
        } else {
            textView.setTextColor(Color.WHITE);
            row.setBackgroundColor(Color.parseColor("#2C2C2C"));
        }
        row.addView(textView);

        TextView timeView = new TextView(context);
        timeView.setText(sdf.format(new Date(msg.getTimestamp())));
        timeView.setTextSize(10f);
        timeView.setTextColor(msg.isLocal() ? Color.parseColor("#555555") : Color.parseColor("#9E9E9E"));
        row.addView(timeView);

        return row;
    }
}
