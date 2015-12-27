package com.misaki0729.cloudconnect.dropbox;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;

import com.misaki0729.cloudconnect.R;


public class DropboxAuth extends Activity {
    public Dropbox dropbox;
    public static ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dropbox);

        dropbox = new Dropbox(this);

        int accountId = 1; // ボタンとかから読み込み

        dropbox.startAuthSession(accountId);

        //dropbox.getMusicInfo(); // debug
        //Log.v("あくせすとーくんー", dropbox.mDBApi.getSession().getOAuth2AccessToken());
    }

    protected void onResume() {
        super.onResume();

        dropbox.storeAccessToken(dropbox.accountId);

    }

}
