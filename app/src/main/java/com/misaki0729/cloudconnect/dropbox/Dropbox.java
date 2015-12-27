package com.misaki0729.cloudconnect.dropbox;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxFileInfo;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AppKeyPair;

import com.misaki0729.cloudconnect.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.concurrent.CountDownLatch;

/**
 * Dropbox操作関連のクラス
 *
 * @author misaki0729
 */
public class Dropbox {
    public static DropboxAPI<AndroidAuthSession> mDBApi;
    final public static String APPKEY = "appkey";
    final public static String APPSECRET = "secret";
    public static int accountId;

    private ArrayList<String> filePath = new ArrayList<>(); // ファイルのパスを格納
    private ArrayList<String> mp3FilePath = new ArrayList<>();
    private ArrayList<MediaMetadataRetriever> metadataList = new ArrayList<>();

    private String accessToken = "accessToken";
    CountDownLatch signal; // 非同期処理のカウント
    Activity activity;

    public Dropbox() {
    }

    public Dropbox(Activity activity) {
        this.activity = activity;
    }

    public void startAuthSession(final int accountId) {

        if (!hasLoadAccessToken(accountId)) {
            AppKeyPair appKeys = new AppKeyPair(APPKEY, APPSECRET);
            AndroidAuthSession session = new AndroidAuthSession(appKeys);
            mDBApi = new DropboxAPI<>(session);
            mDBApi.getSession().startOAuth2Authentication(activity);
            this.accountId = accountId;
        } else {
            // DBから認証情報取得
            setAccessToken(); // debug
        }

        final AsyncTask<Void, Integer, Void> task = new AsyncTask<Void, Integer, Void>() {

            @Override
            protected void onPreExecute() {
                signal = new CountDownLatch(1);
                getMusicInfo();
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    signal.await(); // getMusicInfoが終わるまで待機
                } catch (InterruptedException e) {
                    e.printStackTrace();

                    return null;
                }

                if (mp3FilePath.size() > 0) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getId3Tag();
                        }
                    });
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int[] test = {0, 0};
                        //downloadFile(test);
                        uploadFile(test);
                        //deleteFile(test);
                    }
                });

                return null;
            }

        };
        task.execute();

        //int[] test = {0, 0};
        //downloadAndDelete(test);
    }

    /**
     * アクセストークンを所持しているかどうかを調べるメソッド
     * 所持しているかはデータベースにアクセストークンがあるかどうかで判断する
     *
     * @param accountId アカウント番号
     *                  データベースに登録されていないものは-1か0入れればいいのかな
     * @return データベースに登録されていればtrue
     */
    public boolean hasLoadAccessToken(int accountId) {
        return true; // TODO:DBread
    }

    /**
     * アクセストークンをデータベースに保存するメソッド
     *
     * @param accountId アカウント番号
     * @return DBに保存できればtrue
     */
    public boolean storeAccessToken(int accountId) {
        if (!mDBApi.getSession().authenticationSuccessful()) {
            return false;
        }

        try {
            mDBApi.getSession().finishAuthentication();
            accessToken = mDBApi.getSession().getOAuth2AccessToken();
            Log.v("accessToken", accessToken);

        } catch (IllegalStateException e) {
            Log.e("Dropbox.storeToken", "エラーよー");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    //debug
    public void setAccessToken() {
        AppKeyPair appKeys = new AppKeyPair(APPKEY, APPSECRET);
        mDBApi = new DropboxAPI<>(new AndroidAuthSession(appKeys, accessToken));

    }

    /**
     * Dropbox内のmp3, wavのみを取得, パスをArrayList filePathに保存
     * booleanなんてなかった
     */
    public void getMusicInfo() {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            boolean result;
            ProgressDialog mProgressDialog = new ProgressDialog(activity);
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mProgressDialog.setMessage(activity.getString(R.string.getMusicInfoProgressMessage));
                mProgressDialog.setCancelable(true);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
                mProgressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                result = getEntry("/");

                signal.countDown();

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                mProgressDialog.dismiss();
                mProgressDialog = null;
                return;
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();

                mProgressDialog.dismiss();
                mProgressDialog = null;
                return;
            }

            // 全ファイルの取得
            protected boolean getEntry(String path) {
                Entry entries = null;
                if (isCancelled()) { // キャンセルされたら途中で終了
                    return true;
                }

                try {
                    entries = mDBApi.metadata(path, 1000, null, true, null); // ルート以下のファイルを1000個探索(大きすぎるとエラー出るっぽい、デフォでは25,000らしい)
                    for (Entry e : entries.contents) {
                        if (!e.isDir) { // ディレクトリ？
                            String suffix = getSuffix(e.fileName()); // 拡張子取得
                            if (suffix.equals("mp3") || suffix.equals("wav") || suffix.equals("wave")) {
                                if (suffix.equals("mp3")) {
                                    mp3FilePath.add(e.path);
                                }
                                filePath.add(e.path); // パスを格納
                                Log.v("音楽ファイル", e.path);
                            }
                        } else {
                            getEntry(e.path);
                        }
                    }
                } catch (DropboxUnlinkedException |
                        DropboxServerException |
                        DropboxIOException e) {
                    Log.e("Dropbox.getEntry", "エラーっすよ");
                    e.printStackTrace();
                    return false;
                } catch (DropboxException e) {
                    Log.e("Dropbox.getEntry", "なんか知らんけどエラー出たよー");
                    e.printStackTrace();
                    return false;
                } finally {
                    if (filePath.isEmpty()) {
                        Log.e("FilePathEmpty", "ファイルねーっすよ");
                        return false;
                    }
                }

                return true;
            }

            /**
             * 拡張子を取得するメソッド
             * @param fileName ファイル名
             * @return 拡張子
             */
            public String getSuffix(String fileName) {
                if (fileName == null) {
                    return null;
                }
                int point = fileName.lastIndexOf(".");
                if (point != -1) {
                    return fileName.substring(point + 1);
                }
                return fileName;
            }

        };
        task.execute();
    }

    /**
     * mp3ファイルからid3を取得するメソッド
     * 引数, 返り値なんてなかった
     */
    private void getId3Tag() {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            ProgressDialog mProgressBar = new ProgressDialog(activity);
            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mProgressBar.setProgress(0);
                mProgressBar.setMax(mp3FilePath.size());
                mProgressBar.setTitle(activity.getString(R.string.getId3TagTitle));
                mProgressBar.setMessage(activity.getString(R.string.getId3TagMessage));
                mProgressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressBar.setCancelable(true);
                mProgressBar.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        cancel(true);
                    }
                });
                mProgressBar.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    for (String path : mp3FilePath) {
                        if (isCancelled()) {
                            break;
                        }

                        DropboxAPI.DropboxLink sharePath = mDBApi.media(path, false);
                        mmr.setDataSource(sharePath.url, new HashMap<String, String>());
                        metadataList.add(mmr);

                        publishProgress();

                        // debug
                        String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        Log.d("Title", title);
                    }
                } catch (DropboxUnlinkedException |
                        DropboxServerException |
                        DropboxIOException e) {
                    Log.e("Dropbox.getId3Tag", "エラーなのだー");
                    e.printStackTrace();
                    return null;
                } catch (DropboxException e) {
                    Log.e("getId3Tag", "何かDropbox系のエラーだよー");
                    return null;
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                super.onProgressUpdate(values);
                mProgressBar.incrementProgressBy(1);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                mProgressBar.incrementProgressBy(1);
                if (mProgressBar.getProgress() == mProgressBar.getMax()) {
                    mProgressBar.dismiss();
                    mProgressBar = null;
                }
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                Log.v("cancel", "うわああああ");
                mProgressBar.dismiss();
                mProgressBar = null;
                return;
            }
        };
        task.execute();
    }

    /**
     * mDBApiから認証情報を削除するメソッド
     */
    public void endAuthSession() {
        mDBApi = null;
    }

    /**
     * 引数で受け取ったアカウントに切り替えるメソッド
     *
     * @param accountId アカウント番号
     */
    public void switchAccount(int accountId) {
        endAuthSession();
        // TODO: DBから認証情報呼び出し
        //String accessToken = DB.accessToken;
        //AppKeyPair appKeys = new AppKeyPair(APPKEY, APPSECRET);
        startAuthSession(accountId);
        //mDBApi = new DropboxAPI<>(new AndroidAuthSession(appKeys, accessToken));
    }

    /**
     * DBから認証情報を削除するメソッド
     *
     * @param accountId 削除するアカウント番号
     */
    public void deleteAccount(int accountId) {
        // TODO: DBからレコード削除
    }

    /**
     * ローカルから音楽をアップロードするメソッド
     *
     * @param uploadFileId アップロードする曲のID
     * やっぱりbooleanなんてなかった
     */
    public void uploadFile(final int[] uploadFileId) {
        // TODO: uploadFileIdを用いてデータベースからパス読み込み

        signal = new CountDownLatch(1);
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mProgressDialog = new ProgressDialog(activity);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setMax(uploadFileId.length);
                mProgressDialog.setProgress(0);
                mProgressDialog.setTitle(activity.getString(R.string.uploadProgressTitle));
                mProgressDialog.setMessage(activity.getString(R.string.uploadProgressMessage));
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                upload();
                publishProgress();

                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                super.onProgressUpdate(values);
                mProgressDialog.incrementProgressBy(1);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                mProgressDialog.dismiss();
                signal.countDown();
            }

            public boolean upload() {
                Entry response = null;
                try {
                    String path = null;
                    path = System.getenv("EXTERNAL_STORAGE");
                    File uploadFile = new File(path + "/test.mp3");
                    FileInputStream is = new FileInputStream(uploadFile);
                    response = mDBApi.putFileOverwrite("/ThunderTakePlayer/test.mp3", is, uploadFile.length(), null);
                } catch (FileNotFoundException |
                        IllegalFormatException e) {
                    Log.e("uploadFile", "notFoundError");
                    e.printStackTrace();
                    return false;
                } catch (DropboxUnlinkedException |
                        DropboxServerException |
                        DropboxIOException |
                        DropboxFileSizeException e) {
                    Log.e("uploadFile", "dropboxError");
                    e.printStackTrace();
                    return false;
                } catch (DropboxException e) {
                    Log.e("uploadFile", "dropboxError");
                    e.printStackTrace();
                    return false;
                } finally {
                    if (response == null) {
                        return false;
                    }
                }

                return true;
            }
        };
        task.execute();

    }

    /**
     * Dropboxから音楽をダウンロードするメソッド
     *
     * @param downloadFileId ダウンロードする曲のID
     * @return 正常にダウンロードできればtrue
     */
    public void downloadFile(int[] downloadFileId) {
        // TODO: downloadFileIDを用いてデータベースからパス読み込み

        signal = new CountDownLatch(1);
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                download();

                signal.countDown();
                return null;
            }

            public boolean download() {
                FileOutputStream os = null;
                try {
                    String path = null;
                    path = System.getenv("EXTERNAL_STORAGE");
                    File downloadFile = new File(path + "/test.mp3");
                    os = new FileOutputStream(downloadFile);
                    DropboxFileInfo info = mDBApi.getFile("/ThunderTakePlayer/test.mp3", null, os, null);
                } catch (FileNotFoundException e) {
                    Log.e("downloadFile", "downloadError");
                    e.printStackTrace();
                    return false;
                } catch (DropboxUnlinkedException |
                        DropboxServerException |
                        DropboxIOException |
                        DropboxPartialFileException e) {
                    Log.e("downloadFile", "dropboxError");
                    return false;
                } catch (DropboxException e) {
                    Log.e("downloadFile", "dropboxError");
                    return false;
                } finally {
                    try {
                        if (os == null) {
                            return false;
                        }
                        os.close();
                    } catch (IOException e) {
                        Log.e("downloadFile", "IOException");
                        return false;
                    }
                }
                return true;
            }
        };
        task.execute();
    }

    /**
     * Dropboxのファイルを削除するメソッド
     *
     * @param deleteFileId 削除する曲のID
     */
    public void deleteFile(int[] deleteFileId) {
        // TODO: deleteFileIDを用いてデータベースからパス読み込み

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                delete();
                return null;
            }

            public boolean delete() {
                try {
                    String path = "/ThunderTakePlayer/test.mp3";
                    mDBApi.delete(path);
                } catch (DropboxUnlinkedException |
                        DropboxServerException |
                        DropboxIOException e) {
                    Log.e("deleteFile", "dropboxError");
                    e.printStackTrace();
                    return false;
                } catch (DropboxException e) {
                    Log.e("deleteFile", "dropboxError");
                    e.printStackTrace();
                    return false;
                }

                return true;
            }
        };
        task.execute();
    }

    /**
     * アップロードした後ローカルのファイルを削除するメソッド
     * @param fileId
     */
    public void uploadAndDelete(final int[] fileId) {

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                uploadFile(fileId);
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    signal.await();

                    // debug 実際にはローカルのファイル削除
                    deleteFile(fileId);
                } catch (InterruptedException e) {
                    Log.e("uploadAndDelete", "IntteruptError");
                    e.printStackTrace();
                    return null;
                }
                return null;
            }
        };
        task.execute();
    }

    public void downloadAndDelete(final int[] fileId) {
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                downloadFile(fileId);
            }

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    signal.await();

                    deleteFile(fileId);
                } catch (InterruptedException e) {
                    Log.e("uploadAndDelete", "IntteruptError");
                    e.printStackTrace();
                    return null;
                }
                return null;
            }
        };
        task.execute();
    }
}
