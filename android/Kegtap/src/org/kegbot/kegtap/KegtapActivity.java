package org.kegbot.kegtap;

import java.util.List;

import org.kegbot.api.KegbotApi;
import org.kegbot.api.KegbotApiException;
import org.kegbot.api.KegbotApiImpl;
import org.kegbot.kegtap.service.KegboardService;
import org.kegbot.kegtap.util.PreferenceHelper;
import org.kegbot.proto.Api.TapDetail;
import org.kegbot.proto.Api.TapDetailSet;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.google.common.collect.Lists;

public class KegtapActivity extends CoreActivity {

  public final String LOG_TAG = "KegtapActivity";

  private EventListFragment mEvents;

  private KegbotApi mApi;

  private MyAdapter mTapStatusAdapter;
  private ViewPager mTapStatusPager;
  private List<TapDetail> mTapDetails = Lists.newArrayList();
  private SessionStatsFragment mSession;
  private PreferenceHelper mPrefsHelper;
  private final Handler mHandler = new Handler();
  private GoogleAnalyticsTracker mTracker;

  private final Runnable mRefreshRunnable = new Runnable() {
    @Override
    public void run() {
      Log.d(LOG_TAG, "Reloading events.");
      mEvents.loadEvents();
      mHandler.postDelayed(this, 10000);
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);

    mTracker = GoogleAnalyticsTracker.getInstance();
    mTracker.startNewSession("UA-73794-6", 60, this);

    mTracker.trackPageView("/KegtapActivityOnCreate");

    mPrefsHelper = new PreferenceHelper(this);

    mTapStatusAdapter = new MyAdapter(getSupportFragmentManager());

    mTapStatusPager = (ViewPager) findViewById(R.id.tap_status_pager);
    mTapStatusPager.setAdapter(mTapStatusAdapter);

    mEvents = (EventListFragment) getSupportFragmentManager().findFragmentById(R.id.event_list);

    mSession = (SessionStatsFragment) getSupportFragmentManager().findFragmentById(
        R.id.currentSessionFragment);

    View v = findViewById(R.id.tap_status_pager);
    v.setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
  }

  @Override
  public void onStart() {
    super.onStart();
    mApi = KegbotApiImpl.getSingletonInstance();
    initializeUi();
  }

  @Override
  protected void onResume() {
    super.onResume();
    handleIntent();
    initializeUi();

    mTracker.trackPageView("/KegtapActivityOnResume");
    mHandler.post(mRefreshRunnable);
  }

  @Override
  protected void onPause() {
    mHandler.removeCallbacks(mRefreshRunnable);
    super.onPause();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == R.id.settings) {
      SettingsActivity.startSettingsActivity(this);
      return true;
    } else if (itemId == android.R.id.home) {
      // TODO: navigate up
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleIntent();
  }

  private void handleIntent() {
    final Intent intent = getIntent();
    final String action = intent.getAction();
    Log.d(LOG_TAG, "Handling intent: " + intent);
    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
      final Intent serviceIntent = new Intent(this, KegboardService.class);
      serviceIntent.setAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
      startService(serviceIntent);
    }
  }

  /**
   * Starts the UI.
   * <p>
   * Checks if there is a current kegbot set up: if not, launches settings.
   * <p>
   * If so, loads from last known kegbot.
   */
  private void initializeUi() {
    mApi = KegbotApiImpl.getSingletonInstance();
    mApi.setApiUrl(mPrefsHelper.getKegbotUrl().toString());
    mApi.setApiKey(mPrefsHelper.getApiKey());
    loadUiFragments();
  }

  private void loadUiFragments() {
    new TapLoaderTask().execute();
    // mEvents.loadEvents();
    mSession.loadCurrentSessionDetail();
  }

  public class MyAdapter extends FragmentPagerAdapter {
    public MyAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int arg0) {
      TapStatusFragment frag = new TapStatusFragment();
      frag.setTapDetail(mTapDetails.get(arg0));
      return frag;
    }

    @Override
    public int getCount() {
      return mTapDetails.size();
    }

  }

  private class TapLoaderTask extends AsyncTask<Void, Void, TapDetailSet> {

    @Override
    protected TapDetailSet doInBackground(Void... params) {
      try {
        return mApi.getAllTaps();
      } catch (KegbotApiException e) {
        Log.e(LOG_TAG, "Api error.", e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(TapDetailSet result) {
      if (result == null) {
        return;
      }
      try {
        mTapDetails.clear();
        mTapDetails.addAll(result.getTapsList());
        Log.d(LOG_TAG, "Updating adapter, taps: " + mTapDetails.size());
        mTapStatusPager.setAdapter(mTapStatusAdapter);
      } catch (Throwable e) {
        Log.wtf("TapStatusFragment", "UNCAUGHT EXCEPTION", e);
      }
    }
  }

}
