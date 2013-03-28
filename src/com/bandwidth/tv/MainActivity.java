package com.bandwidth.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.bandwidth.tv.util.SystemUiHider;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 * 
 * @see SystemUiHider
 */
public class MainActivity extends Activity {
    
    private static final String TAG = MainActivity.class.getSimpleName();
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;
    
    private static final int RELOAD = 1000;
    private static final int RELOAD_INTERVAL = 1800000;
    private static final int CONFIG = 101;
    
    private ViewPagerFlipper mViewPager;
    private Button mBtnControl;
    private Button mBtnConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);
        getWindow().setFormat(PixelFormat.RGBA_8888);

        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
        
        setContentView(R.layout.activity_fullscreen);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        mViewPager = (ViewPagerFlipper)findViewById(R.id.fullscreen_content);

        // Set up an instance of SystemUiHider to control the system UI for
        // this activity.
        mSystemUiHider = SystemUiHider.getInstance(this, mViewPager,
                HIDER_FLAGS);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    // Cached values.
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            // If the ViewPropertyAnimator API is available
                            // (Honeycomb MR2 and later), use it to animate the
                            // in-layout UI controls at the bottom of the
                            // screen.
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView
                                    .animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            // If the ViewPropertyAnimator APIs aren't
                            // available, simply show or hide the in-layout UI
                            // controls.
                            controlsView.setVisibility(visible ? View.VISIBLE
                                    : View.GONE);
                        }

                        if (visible && AUTO_HIDE) {
                            // Schedule a hide().
                            delayedHide(AUTO_HIDE_DELAY_MILLIS);
                        }
                    }
                });

        // Set up the user interaction to manually show or hide the system UI.
        mViewPager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TOGGLE_ON_CLICK) {
                    mSystemUiHider.toggle();
                } else {
                    mSystemUiHider.show();
                }
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        mBtnControl = (Button)findViewById(R.id.btn_control);
        mBtnControl.setOnTouchListener(
                mDelayHideTouchListener);
        mBtnControl.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "toggleFlipping");
                mViewPager.toggleFlipping();
                
                if (mViewPager.isFlipping()) {
                    mBtnControl.setText(R.string.lbl_stop);
                } else {
                    mBtnControl.setText(R.string.lbl_start);
                }
            }
        });
        
        mBtnConfig = (Button)findViewById(R.id.btn_config);
        mBtnConfig.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mViewPager.stopFlipping();
//                new ConfigFetch().execute();
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), CONFIG);
            }
        });
        
        WebviewPagerAdapter mWebviewAdapater = new WebviewPagerAdapter(this, null);
        mViewPager.setAdapter(mWebviewAdapater);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String sheetId = prefs.getString("sheet_id", null);
        if (sheetId != null) {
            new ConfigFetch().execute();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(250);
    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch(requestCode) {
        case CONFIG:
            if (resultCode == RESULT_OK) {
                new ConfigFetch().execute();
            }
        }
    }
    
    
    protected void setPages(List<Page> pages) {
        mViewPager.setOffscreenPageLimit(pages.size());
        WebviewPagerAdapter mWebviewAdapater = new WebviewPagerAdapter(this, pages);
        mViewPager.setAdapter(mWebviewAdapater);
        // start flipping
        mViewPager.startFlipping();
        mBtnControl.setText(R.string.lbl_stop);
    }
    
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };
    
    private static final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == RELOAD) {
                if (null != msg.obj && msg.obj instanceof WebView) {
                    WebView webView = (WebView)msg.obj;
                    webView.reload();
                    Message nextMsg = obtainMessage(RELOAD, webView);
                    sendMessageDelayed(nextMsg, RELOAD_INTERVAL);
                }
            }
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHandler.removeCallbacks(mHideRunnable);
        mHandler.postDelayed(mHideRunnable, delayMillis);
    }
    
    private class WebviewPagerAdapter extends PagerAdapter implements ViewPagerFlipper.Callback {
        
        private Context mContext;
        private List<Page> mPages;
        
        public WebviewPagerAdapter(Context context, List<Page> pages) {
            mContext = context;
            mPages = pages;
        }

        @Override
        public int getCount() {
            if (mPages == null) {
                return 0;
            }
            return mPages.size();
        }
        
        @Override
        public Object instantiateItem(ViewGroup collection, int position) {
            if (mPages == null) {
                return null;
            }
            WebView webview = new WebView(mContext);
            webview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            webview.getSettings().setJavaScriptEnabled(true);
            webview.getSettings().setPluginState(PluginState.ON);
            webview.getSettings().setBuiltInZoomControls(true);
            webview.getSettings().setUseWideViewPort(true);
            webview.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            webview.getSettings().setAppCacheEnabled(false);
            webview.getSettings().setAppCacheMaxSize(0);
            webview.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int progress) {
                    Log.d(TAG, "progress -> " + progress + "; url -> " + view.getUrl());
                    MainActivity.this.setProgress(progress * 1000);
                }
            });
            webview.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode,
                        String description, String failingUrl) {
                    Toast.makeText(MainActivity.this,
                            "Oh no! " + description, Toast.LENGTH_SHORT).show();
                }
            });
            
            Page page = mPages.get(position);
            
            if (Type.IMAGE.equals(page.display)) {
                int height = getWindowManager().getDefaultDisplay().getHeight();
                String data = "<html><head><title>Example</title><meta name=\"viewport\"\"content=\"height="+height+", initial-scale=1 \" /></head>";
                data = data+"<body><center><img height=\""+(height-200)+"\" src=\""+mPages.get(position).url+"&"+System.currentTimeMillis()+"\" /></center></body></html>";
                webview.loadData(data, "text/html", null);                
            } else {
                
                webview.loadUrl(mPages.get(position).url+"&"+System.currentTimeMillis());
            }

            collection.addView(webview);
            
            // send to reload pool
            Message msg = mHandler.obtainMessage(RELOAD, webview);
            mHandler.sendMessageDelayed(msg, RELOAD_INTERVAL);

            return webview;
        }
        
        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            Log.d(TAG, "remove -> " + position);
            collection.removeView((WebView)view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return (view == object);
        }
        
        @Override
        public void finishUpdate(ViewGroup collection) {
            
        }
        
        @Override
        public void restoreState(Parcelable bundle, ClassLoader cl) {
            
        }
        
        @Override
        public Parcelable saveState() {
            return null;
        }
        
        @Override
        public void startUpdate(ViewGroup collection) {
            
        }

        @Override
        public int getFlipInterval(int position) {
            Log.d(TAG, "position -> " + position);
            return mPages.get(position).interval;
        }
        
    }
    
    private enum Type {
        IMAGE, HTML;
        
        public static Type getType(String type) {
            if (type.equals("image")) {
                return IMAGE;
            }
            
            return HTML;
        }
    }
    
    private class Page {
        public String title;
        public String url;
        public int interval;
        public Type display;
    }
    
    private class ConfigFetch extends AsyncTask<Void, Void, List<Page>> {

        @Override
        protected List<Page> doInBackground(Void... params) {
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String sheetId = prefs.getString("sheet_id", null);
            
            if (sheetId == null) {
                return null;
            }
            
            List<Page> pages = new ArrayList<Page>();
            
            HttpClient client = new DefaultHttpClient();
            HttpContext ctx = new BasicHttpContext();
            HttpGet get = new HttpGet("https://docs.google.com/spreadsheet/pub?key=0AnvKfvUoHlQkdFc4UURjMUh5ZUtpUURURmctMWJOWGc&single=true&gid=" + sheetId + "&output=csv");
            
            try {
                HttpResponse res = client.execute(get, ctx);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
                
                boolean skip = true;

                String line;
                while ( (line = reader.readLine()) != null) {
                    if (!skip) {
                        String[] row = line.split(",");
                        Page page = new Page();
                        page.title = row[0];
                        page.url = row[3];
                        page.interval = Integer.parseInt(row[1]) * 1000;
                        page.display = Type.getType(row[2]);
                        pages.add(page);
                    }
                    skip = false;
                }
            } catch (ClientProtocolException e) {
                Log.d(TAG, "could not get config", e);
            } catch (IOException e) {
                Log.d(TAG, "could not get config", e);
            }
            
            return pages;
        }
        
        @Override
        protected void onPostExecute(List<Page> results) {
            if (results != null && results.size() > 0) {
                setPages(results);
            }
        }
        
    }
}
