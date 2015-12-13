package com.misaki0729.cloudconnect;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ViewSwitcher;

import com.misaki0729.cloudconnect.onedrive.AuthenticationInfo;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

public class onedrivetest extends FragmentActivity {

    public static String code = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onedrive_test);

        Button button = (Button)findViewById(R.id.login);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebView((WebView) findViewById(R.id.wv));
            }
        });
    }

    public String getCode(String title) {
        String code = null;
        String codeKey = "code=";
        int idx = title.indexOf(codeKey);
        if (idx != -1) { // 認証成功ページだった
            code = title.substring(idx + codeKey.length()); // 「code」を切り出し
        }
        return code;
    }

    public void openWebView(WebView wv) {
        wv.getSettings().setJavaScriptEnabled(true);
        wv.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) { // ページ読み込み完了時

                ViewSwitcher vs = (ViewSwitcher)findViewById(R.id.vs);

                // ページurlからコードを取得
                String pageUrl = view.getUrl();
                code = getCode(pageUrl);

                if (code == null) {
                    Log.v("onPageFinished", "コード取得成功ページ以外 url=" + url);
                    if (!(vs.getCurrentView() instanceof WebView)) { // WebViewが表示されてなかったら
                        vs.showNext(); // Web認証画面表示
                    }
                } // コード取得成功
                else {
                    vs.showPrevious(); // 元の画面に戻る
                    Log.v("onPageFinished", "コード取得成功 code=" + code);

                    Bundle bundle = new Bundle();
                    bundle.putString("format", "json");
                    getSupportLoaderManager().initLoader(0, bundle, callbacks);
                }

            }
        });

        String url = "https://login.live.com/oauth20_authorize.srf?client_id=0000000123ABCD&scope=wl.signin%20wl.basic%20wl.offline_access%20wl.skydrive_update&response_type=code&redirect_uri=https://login.live.com/oauth20_desktop.srf";
        wv.loadUrl(url);
    }

    private LoaderCallbacks<String> callbacks = new LoaderCallbacks<String>() {
        @Override
        public void onLoadFinished(Loader<String> loader, String data) {
            getSupportLoaderManager().destroyLoader(loader.getId());
        }

        @Override
        public void onLoaderReset(Loader<String> loader) {
        }

        @Override
        public Loader<String> onCreateLoader(int id, Bundle args) {
            CustomLoader loader = new CustomLoader(getApplicationContext(), args);
            loader.forceLoad();
            return loader;
        }
    };

    public static class CustomLoader extends AsyncTaskLoader<String> {
        private String mFormat;

        public CustomLoader(Context context, Bundle bundle) {
            super(context);
            mFormat = bundle.getString("format");
        }

        @Override
        public String loadInBackground() {
            return getJson();
        }

        private String getJson() {
            RestTemplate template = new RestTemplate();
            template.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
            String url = "https://login.live.com/oauth20_token.srf?client_id=0000000123ABCD&client_secret=abcdefghzyxwvutsr12345&code="+onedrivetest.code+"&lc=1041&grant_type=authorization_code&redirect_uri=https://login.live.com/oauth20_desktop.srf";
            try {
                ResponseEntity<AuthenticationInfo> responseEntity = template.exchange(url, HttpMethod.GET, null, AuthenticationInfo.class);
                AuthenticationInfo res = responseEntity.getBody();
                Log.v("でーきたー", res.toString());
                return res.toString();
            } catch (Exception e) {
                Log.d("Error", e.toString());
                return null;
            }
        }
    }
}
