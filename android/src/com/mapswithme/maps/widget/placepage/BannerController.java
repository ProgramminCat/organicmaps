package com.mapswithme.maps.widget.placepage;

import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mapswithme.maps.R;
import com.mapswithme.maps.ads.AdTracker;
import com.mapswithme.maps.ads.Banner;
import com.mapswithme.maps.ads.CompoundNativeAdLoader;
import com.mapswithme.maps.ads.MwmNativeAd;
import com.mapswithme.maps.ads.NativeAdError;
import com.mapswithme.maps.ads.NativeAdListener;
import com.mapswithme.util.Config;
import com.mapswithme.util.UiUtils;
import com.mapswithme.util.log.Logger;
import com.mapswithme.util.log.LoggerFactory;
import com.mapswithme.util.statistics.Statistics;

import java.util.List;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.mapswithme.util.SharedPropertiesUtils.isShowcaseSwitchedOnLocal;
import static com.mapswithme.util.statistics.Statistics.EventName.PP_BANNER_CLICK;
import static com.mapswithme.util.statistics.Statistics.EventName.PP_BANNER_SHOW;

final class BannerController
{
  private static final Logger LOGGER = LoggerFactory.INSTANCE
      .getLogger(LoggerFactory.Type.MISC);
  private static final String TAG = BannerController.class.getName();

  private static final int MAX_MESSAGE_LINES = 100;
  private static final int MIN_MESSAGE_LINES = 3;
  private static final int MAX_TITLE_LINES = 2;
  private static final int MIN_TITLE_LINES = 1;

  private static boolean isTouched(@Nullable View view, @NonNull MotionEvent event)
  {
    return view != null && !UiUtils.isHidden(view) && UiUtils.isViewTouched(event, view);
  }

  @Nullable
  private List<Banner> mBanners;
  @NonNull
  private final View mFrame;
  @NonNull
  private final ImageView mIcon;
  @NonNull
  private final TextView mTitle;
  @NonNull
  private final TextView mMessage;
  @NonNull
  private final TextView mActionSmall;
  @NonNull
  private final TextView mActionLarge;
  @NonNull
  private final View mAds;

  private final float mCloseFrameHeight;

  @Nullable
  private final BannerListener mListener;

  private boolean mOpened = false;
  private boolean mError = false;
  @Nullable
  private MwmNativeAd mCurrentAd;
  @NonNull
  private CompoundNativeAdLoader mAdsLoader;
  @Nullable
  private AdTracker mAdTracker;

  BannerController(@NonNull View bannerView, @Nullable BannerListener listener,
                   @NonNull CompoundNativeAdLoader loader, @Nullable AdTracker tracker)
  {
    LOGGER.d(TAG, "Constructor()");
    mFrame = bannerView;
    mListener = listener;
    Resources resources = mFrame.getResources();
    mCloseFrameHeight = resources.getDimension(R.dimen.placepage_banner_height);
    mIcon = (ImageView) bannerView.findViewById(R.id.iv__banner_icon);
    mTitle = (TextView) bannerView.findViewById(R.id.tv__banner_title);
    mMessage = (TextView) bannerView.findViewById(R.id.tv__banner_message);
    mActionSmall = (TextView) bannerView.findViewById(R.id.tv__action_small);
    mActionLarge = (TextView) bannerView.findViewById(R.id.tv__action_large);
    mAds = bannerView.findViewById(R.id.tv__ads);
    loader.setAdListener(new MyNativeAdsListener());
    mAdsLoader = loader;
    mAdTracker = tracker;
    mFrame.setOnClickListener(new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        View view = mOpened ? mFrame.findViewById(R.id.tv__action_large)
                            : mFrame.findViewById(R.id.tv__action_small);
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 0.3f, 1f);
        anim.setDuration(700);
        anim.start();
      }
    });
  }

  private void setErrorStatus(boolean value)
  {
    mError = value;
  }

  boolean hasErrorOccurred()
  {
    return mError;
  }

  private void updateVisibility()
  {
    if (mBanners == null)
      return;

    UiUtils.showIf(!hasErrorOccurred(), mFrame);
    if ((mAdsLoader.isAdLoading() || hasErrorOccurred())
        && mCurrentAd == null)
    {
      UiUtils.hide(mIcon, mTitle, mMessage, mActionSmall, mActionLarge, mAds);
    }
    else
    {
      UiUtils.show(mIcon, mTitle, mMessage, mActionSmall, mActionLarge, mAds);
      if (mOpened)
        UiUtils.hide(mActionSmall);
      else
        UiUtils.hide(mActionLarge, mIcon);
    }
  }

  void updateData(@Nullable List<Banner> banners)
  {
    if (mBanners != null && !mBanners.equals(banners))
    {
      onChangedVisibility(false);
      mCurrentAd = null;
    }

    UiUtils.hide(mFrame);
    setErrorStatus(false);

    mBanners = banners;
    if (mBanners == null || !isShowcaseSwitchedOnLocal()
        || Config.getAdForbidden())
    {
      return;
    }

    UiUtils.show(mFrame);

    mAdsLoader.loadAd(mFrame.getContext(), mBanners);
    updateVisibility();
  }

  boolean isBannerVisible()
  {
    return !UiUtils.isHidden(mFrame);
  }

  void open()
  {
    if (!isBannerVisible() || mBanners == null || mOpened)
      return;

    mOpened = true;
    setFrameHeight(WRAP_CONTENT);
    if (mCurrentAd != null)
    {
      loadIcon(mCurrentAd);
      Statistics.INSTANCE.trackPPBanner(PP_BANNER_SHOW, mCurrentAd, 1);
    }
    mMessage.setMaxLines(MAX_MESSAGE_LINES);
    mTitle.setMaxLines(MAX_TITLE_LINES);
    updateVisibility();

  }

  boolean close()
  {
    if (!isBannerVisible() || mBanners == null || !mOpened)
      return false;

    mOpened = false;
    setFrameHeight((int) mCloseFrameHeight);
    UiUtils.hide(mIcon);
    mMessage.setMaxLines(MIN_MESSAGE_LINES);
    mTitle.setMaxLines(MIN_TITLE_LINES);
    updateVisibility();

    return true;
  }

  int getLastBannerHeight()
  {
    return mFrame.getHeight();
  }

  private void setFrameHeight(int height)
  {
    ViewGroup.LayoutParams lp = mFrame.getLayoutParams();
    lp.height = height;
    mFrame.setLayoutParams(lp);
  }

  private void loadIcon(@NonNull MwmNativeAd ad)
  {
    UiUtils.show(mIcon);
    ad.loadIcon(mIcon);
  }

  void onChangedVisibility(boolean isVisible)
  {
    if (mAdTracker == null || mCurrentAd == null)
      return;

    if (isVisible)
      mAdTracker.onViewShown(mCurrentAd.getProvider(), mCurrentAd.getBannerId());
    else
      mAdTracker.onViewHidden(mCurrentAd.getProvider(), mCurrentAd.getBannerId());
  }

  private void fillViews(@NonNull MwmNativeAd data)
  {
    mTitle.setText(data.getTitle());
    mMessage.setText(data.getDescription());
    mActionSmall.setText(data.getAction());
    mActionLarge.setText(data.getAction());
  }

  private void loadIconAndOpenIfNeeded(@NonNull MwmNativeAd data)
  {
    if (UiUtils.isLandscape(mFrame.getContext()))
    {
      if (!mOpened)
        open();
      else
        loadIcon(data);
    }
    else if (!mOpened)
    {
      close();
      Statistics.INSTANCE.trackPPBanner(PP_BANNER_SHOW, data, 0);
    }
    else
    {
      loadIcon(data);
    }
  }

  boolean isActionButtonTouched(@NonNull MotionEvent event)
  {
    return isTouched(mFrame, event);
  }

  interface BannerListener
  {
    void onSizeChanged();
  }

  private class MyNativeAdsListener implements NativeAdListener
  {
    @Override
    public void onAdLoaded(@NonNull MwmNativeAd ad)
    {
      LOGGER.d(TAG, "onAdLoaded, title = " + ad.getTitle() + " provider = " + ad.getProvider());
      if (mBanners == null)
        return;

      mCurrentAd = ad;

      updateVisibility();

      fillViews(ad);

      ad.registerView(mFrame);

      loadIconAndOpenIfNeeded(ad);

      if (mAdTracker != null)
      {
        onChangedVisibility(isBannerVisible());
        mAdTracker.onContentObtained(ad.getProvider(), ad.getBannerId());
      }

      if (mListener != null && mOpened)
        mListener.onSizeChanged();
    }

    @Override
    public void onError(@NonNull MwmNativeAd ad, @NonNull NativeAdError error)
    {
      if (mBanners == null)
        return;

      boolean isNotCached = mCurrentAd == null;
      setErrorStatus(isNotCached);
      updateVisibility();

      if (mListener != null && isNotCached)
        mListener.onSizeChanged();

      Statistics.INSTANCE.trackPPBannerError(ad, error, mOpened ? 1 : 0);
    }

    @Override
    public void onClick(@NonNull MwmNativeAd ad)
    {
      Statistics.INSTANCE.trackPPBanner(PP_BANNER_CLICK, ad, mOpened ? 1 : 0);
    }
  }
}
