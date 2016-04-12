package com.vrv.litedood.ui.activity;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListViewCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;

import com.vrv.imsdk.SDKManager;
import com.vrv.imsdk.model.Chat;
import com.vrv.imsdk.model.ChatMsg;
import com.vrv.imsdk.model.ChatMsgList;
import com.vrv.litedood.R;
import com.vrv.litedood.adapter.MessageAdapter;
import com.vrv.litedood.bpo.LiteDoodMessageProvider;
import com.vrv.litedood.bpo.LiteDoodRequestHandler;
import com.vrv.litedood.common.sdk.action.RequestHandler;
import com.vrv.litedood.common.sdk.action.RequestHelper;
import com.vrv.litedood.common.sdk.utils.BaseInfoBean;
import com.vrv.litedood.dto.MessageDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kinee on 2016/3/31.
 */
public class MessageActivity extends AppCompatActivity {
    private static final String TAG = MessageActivity.class.getSimpleName();
    private static final String ID_USER_INFO="USER_INFO";
    private static final String LAST_MESSAGE_ID = "lastmsgid";

    private Toolbar toolbarMessage;
    private BaseInfoBean userInfo;
    private List<ChatMsg> chatMsgQueue = new ArrayList<>();
    private ChatMsgList chatMsgList;
    private ListViewCompat lvMessage;
    private MessageAdapter messageAdapter;

    private ContentResolver resolver;

    public static void startMessageActivity(Activity activity, Chat chat) {
        Intent intent = new Intent();
        intent.putExtra(ID_USER_INFO, BaseInfoBean.chat2BaseInfo(chat));
        intent.putExtra(LAST_MESSAGE_ID, chat.getLastMsgID());
        intent.setClass(activity, MessageActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void startMessageActivity1(Activity activity, BaseInfoBean bean) {
        Intent intent = new Intent();
        intent.putExtra(ID_USER_INFO, bean);
        intent.setClass(activity, MessageActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        userInfo = (BaseInfoBean) getIntent().getParcelableExtra(MessageActivity.ID_USER_INFO);
        resolver = getContentResolver();

        initToolbar();
        initMessageData();

    }

    private void initToolbar() {
        toolbarMessage = (Toolbar)findViewById(R.id.toolbarMessage);
        setSupportActionBar(toolbarMessage);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(userInfo.getName());

    }

    private void initMessageData() {
        messageAdapter = new MessageAdapter(MessageActivity.this, chatMsgQueue);
        lvMessage = (ListViewCompat)findViewById(R.id.listMessage);
        lvMessage.setAdapter(messageAdapter);


        chatMsgList = SDKManager.instance().getChatMsgList();
        chatMsgList.setReceiveListener(userInfo.getID(), new ChatMsgList.OnReceiveChatMsgListener() {
            @Override
            public void onReceive(ChatMsg msg) {
                addChatMsg(msg);
            }

            @Override
            public void onUpdate(ChatMsg msg) {

                updateMsgStatus(msg);
            }
        });

        setMessageHistory(userInfo.getID(), getIntent().getLongExtra(LAST_MESSAGE_ID, -1));


        final AppCompatButton btnSendMessage = (AppCompatButton)findViewById(R.id.btnSendMessage);
        final AppCompatEditText edtMessage = (AppCompatEditText)findViewById(R.id.edtMessage);

        btnSendMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String txt = edtMessage.getText().toString();
                if (!txt.isEmpty()) {
                    RequestHelper.sendTxt(userInfo.getID(), txt, null, new LiteDoodRequestHandler(LiteDoodRequestHandler.HANDLER_SEND_MESSAGE, MessageActivity.this));
                    edtMessage.getText().clear();
                }
            }
        });
    }

    private void addChatMsg(ChatMsg chatMsg) {
        if (chatMsg == null) return;
        if((chatMsg.getTargetID() == userInfo.getID()) && (chatMsgQueue != null)) {
            //Log.v(TAG, "in add, MsgQueueSize: " + String.valueOf(chatMsgQueue.size()));
            chatMsgQueue.add(chatMsg);
            messageAdapter.notifyDataSetChanged();
            //saveMessageToDB(chatMsg);

        }
    }

    private void updateMsgStatus(ChatMsg chatMsg) {
        if (chatMsg == null) {
            return;
        }
        int size = chatMsgQueue.size();
        Log.v(TAG, "in Update, MsgQueueSize: " + String.valueOf(size));
        for (int i = size - 1; i >= 0; i--) {
            if (chatMsg.getLocalID() == chatMsgQueue.get(i).getLocalID()) {
                chatMsgQueue.set(i, chatMsg);
                messageAdapter.notifyDataSetChanged();
                return;
            }
        }
    }

    /*private void setMessageHistory(long targetID) {
        String uriString = LiteDoodMessageProvider.SCHEME + LiteDoodMessageProvider.AUTHORITY + "/" + MessageDTO.TABLE_NAME;
        Uri uri = Uri.parse(uriString);
        Log.v(TAG, "receive ID: " + String.valueOf(receiveID));
        Cursor temp = resolver.query(uri, MessageDTO.getAllColumns(), null, null, null);
        ArrayList<ChatMsg> tempList = MessageDTO.toChatMsg(temp);


        Cursor chatMsgHistory = resolver.query(uri,
                MessageDTO.getAllColumns(),
                MessageDTO.TABLE_MESSAGE_COLUMN_RECEIVEID
                    + "=? or "
                    + MessageDTO.TABLE_MESSAGE_COLUMN_SENDID
                    + "=? ", new String[] {String.valueOf(targetID), String.valueOf(targetID)},
                null);
        ArrayList<ChatMsg> oldMsg = MessageDTO.toChatMsg(chatMsgHistory);

        for(ChatMsg msg : oldMsg) {
            Log.v(TAG, msg.toString() + " id:" + msg.getId());
        }
        if ((oldMsg != null) && (oldMsg.size() > 0)) {
            chatMsgQueue.addAll(oldMsg);
            messageAdapter.notifyDataSetChanged();
        };
    }*/

    private void setMessageHistory(long targetID, long lastChatID) {
        RequestHelper.getChatHistory(targetID, lastChatID, 16, new HistoryMessageHandler(1));
    }

    private void saveMessageToDB(ChatMsg chatMsg) {

        String uriString = LiteDoodMessageProvider.SCHEME + LiteDoodMessageProvider.AUTHORITY + "/" + MessageDTO.TABLE_NAME;
        Uri insertUri = Uri.parse(uriString);
        resolver.insert(insertUri, MessageDTO.convertChatMessage(chatMsg));
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                //NavUtils.navigateUpFromSameTask(this);
                MainActivity.startMainActivity(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class HistoryMessageHandler extends RequestHandler {
        private int nType;

        public HistoryMessageHandler(int type) {
            this.nType = type;
        }
        @Override
        public void handleSuccess(Message msg) {
            if (nType == 1) {
                ArrayList<ChatMsg> chatMsgArray = msg.getData().getParcelableArrayList("data");

                chatMsgQueue.clear();
                chatMsgQueue.addAll(chatMsgArray);
                messageAdapter.notifyDataSetChanged();

            }
        }
    }
}