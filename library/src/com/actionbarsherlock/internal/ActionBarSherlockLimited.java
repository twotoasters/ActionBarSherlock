package com.actionbarsherlock.internal;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.actionbarsherlock.internal.Helpers.getResources_getBoolean;
import java.util.HashMap;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import com.actionbarsherlock.ActionBarSherlock;
import com.actionbarsherlock.R;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.internal.app.ActionBarLimitedImpl;
import com.actionbarsherlock.internal.view.menu.ActionMenuPresenter;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuItemImpl;
import com.actionbarsherlock.internal.view.menu.MenuPresenter;
import com.actionbarsherlock.internal.widget.ActionBarLimitedContainer;
import com.actionbarsherlock.internal.widget.ActionBarLimitedView;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.ActionMode.Callback;
import com.actionbarsherlock.view.MenuItem;

public class ActionBarSherlockLimited extends ActionBarSherlock {
    /** Window features which are enabled by default. */
    protected static final int DEFAULT_FEATURES = 0;


    public ActionBarSherlockLimited(Activity activity, int flags) {
        super(activity, flags);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Properties
    ///////////////////////////////////////////////////////////////////////////

    /** Whether or not the device has a dedicated menu key button. */
    private boolean mReserveOverflow;
    /** Lazy-load indicator for {@link #mReserveOverflow}. */
    private boolean mReserveOverflowSet = false;

    /** Current menu instance for managing action items. */
    private MenuBuilder mMenu;
    /** Map between native options items and sherlock items. */
    protected HashMap<android.view.MenuItem, MenuItemImpl> mNativeItemMap;

    /** Parent view of the window decoration (action bar, mode, etc.). */
    private ViewGroup mDecor;
    /** Parent view of the activity content. */
    private ViewGroup mContentParent;

    /** Whether or not the title is stable and can be displayed. */
    private boolean mIsTitleReady = false;

    /** Implementation which backs the action bar interface API. */
    private ActionBarLimitedImpl mActionBar;
    /** Main action bar view which displays the core content. */
    private ActionBarLimitedView mActionBarView;
    /** Relevant window and action bar features flags. */
    private int mFeatures = DEFAULT_FEATURES;
    /** Relevant user interface option flags. */
    private int mUiOptions = 0;


    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle and interaction callbacks when delegating
    ///////////////////////////////////////////////////////////////////////////

    /** Window callback for the home action item. */
    private final com.actionbarsherlock.view.Window.Callback mWindowCallback = new com.actionbarsherlock.view.Window.Callback() {
        @Override
        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return callbackOptionsItemSelected(item);
        }
    };

    /** Action bar menu-related callbacks. */
    private final MenuPresenter.Callback mMenuPresenterCallback = new MenuPresenter.Callback() {
        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            // TODO Auto-generated method stub
        }
    };

    /** Native menu item callback which proxies to our callback. */
    protected final android.view.MenuItem.OnMenuItemClickListener mNativeItemListener = new android.view.MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(android.view.MenuItem item) {
            if (DEBUG) Log.d(TAG, "[mNativeItemListener.onMenuItemClick] item: " + item);

            final MenuItemImpl sherlockItem = mNativeItemMap.get(item);
            if (sherlockItem != null) {
                sherlockItem.invoke();
            } else {
                Log.e(TAG, "Options item \"" + item + "\" not found in mapping");
            }

            return true; //Do not allow continuation of native handling
        }
    };

    /** Menu callbacks triggered with actions on our items. */
    protected final MenuBuilder.Callback mMenuBuilderCallback = new MenuBuilder.Callback() {
        @Override
        public void onMenuModeChange(MenuBuilder menu) {
            reopenMenu(true);
        }

        @Override
        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            return callbackOptionsItemSelected(item);
        }
    };


    ///////////////////////////////////////////////////////////////////////////
    // Instance methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Determine whether or not the device has a dedicated menu key.
     *
     * @return {@code true} if native menu key is present.
     */
    private boolean isReservingOverflow() {
        if (!mReserveOverflowSet) {
            mReserveOverflow = ActionMenuPresenter.reserveOverflow(mActivity);
            mReserveOverflowSet = true;
        }
        return mReserveOverflow;
    }

    @Override
    public ActionBar getActionBar() {
        if (DEBUG) Log.d(TAG, "[getActionBar]");

        initActionBar();
        return mActionBar;
    }

    protected void initActionBar() {
        if (DEBUG) Log.d(TAG, "[initActionBar]");

        // Initializing the window decor can change window feature flags.
        // Make sure that we have the correct set before performing the test below.
        if (mDecor == null) {
            installDecor();
        }

        if ((mActionBar != null) || !hasFeature(Window.FEATURE_ACTION_BAR) || mActivity.isChild()) {
            return;
        }

        mActionBar = new ActionBarLimitedImpl(mActivity, mFeatures);

        if (!mIsDelegate) {
            //We may never get another chance to set the title
            mActionBarView.setWindowTitle(mActivity.getTitle());
        }
    }

    @Override
    protected Context getThemedContext() {
        return mActionBar.getThemedContext();
    }

    @Override
    public ActionMode startActionMode(Callback callback) {
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Lifecycle and interaction callbacks when delegating
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void dispatchConfigurationChanged(Configuration newConfig) {
        if (DEBUG) Log.d(TAG, "[dispatchConfigurationChanged] newConfig: " + newConfig);

        if (mActionBar != null) {
            mActionBar.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void dispatchPause() {
        if (DEBUG) Log.d(TAG, "[dispatchPause]");

        if (mActionBarView != null && mActionBarView.isOverflowMenuShowing()) {
            mActionBarView.hideOverflowMenu();
        }
    }

    @Override
    public void dispatchStop() {
        if (DEBUG) Log.d(TAG, "[dispatchStop]");

        //if (mActionBar != null) {
        //    mActionBar.setShowHideAnimationEnabled(false);
        //}
    }

    @Override
    public void dispatchInvalidateOptionsMenu() {
        if (DEBUG) Log.d(TAG, "[dispatchInvalidateOptionsMenu]");

        if (mMenu == null) {
            Context context = mActivity;
            if (mActionBar != null) {
                TypedValue outValue = new TypedValue();
                mActivity.getTheme().resolveAttribute(R.attr.actionBarWidgetTheme, outValue, true);
                if (outValue.resourceId != 0) {
                    //We are unable to test if this is the same as our current theme
                    //so we just wrap it and hope that if the attribute was specified
                    //then the user is intentionally specifying an alternate theme.
                    context = new ContextThemeWrapper(context, outValue.resourceId);
                }
            }
            mMenu = new MenuBuilder(context);
            mMenu.setCallback(mMenuBuilderCallback);
        }

        mMenu.stopDispatchingItemsChanged();
        mMenu.clear();

        if (!callbackCreateOptionsMenu(mMenu)) {
            mMenu = null;
            if (mActionBar != null) {
                if (DEBUG) Log.d(TAG, "[dispatchInvalidateOptionsMenu] setting action bar menu to null");
                mActionBarView.setMenu(null, mMenuPresenterCallback);
            }
            return;
        }

        if (!callbackPrepareOptionsMenu(mMenu)) {
            if (mActionBar != null) {
                if (DEBUG) Log.d(TAG, "[dispatchInvalidateOptionsMenu] setting action bar menu to null");
                mActionBarView.setMenu(null, mMenuPresenterCallback);
            }
            mMenu.startDispatchingItemsChanged();
            return;
        }

        //TODO figure out KeyEvent? See PhoneWindow#preparePanel
        KeyCharacterMap kmap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        mMenu.setQwertyMode(kmap.getKeyboardType() != KeyCharacterMap.NUMERIC);
        mMenu.startDispatchingItemsChanged();

        if (DEBUG) Log.d(TAG, "[dispatchInvalidateOptionsMenu] setting action bar menu to " + mMenu);
        mActionBarView.setMenu(mMenu, mMenuPresenterCallback);
    }

    @Override
    public boolean dispatchOpenOptionsMenu() {
        if (DEBUG) Log.d(TAG, "[dispatchOpenOptionsMenu]");

        if (!isReservingOverflow()) {
            return false;
        }

        return mActionBarView.showOverflowMenu();
    }

    @Override
    public boolean dispatchCloseOptionsMenu() {
        if (DEBUG) Log.d(TAG, "[dispatchCloseOptionsMenu]");

        if (!isReservingOverflow()) {
            return false;
        }

        return mActionBarView.hideOverflowMenu();
    }

    @Override
    public void dispatchPostCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "[dispatchOnPostCreate]");

        if (mIsDelegate) {
            mIsTitleReady = true;
        }

        if (mDecor == null) {
            initActionBar();
        }
    }

    @Override
    public boolean dispatchCreateOptionsMenu(android.view.Menu menu) {
        return true;
    }

    @Override
    public boolean dispatchPrepareOptionsMenu(android.view.Menu menu) {
        if (DEBUG) Log.d(TAG, "[dispatchPrepareOptionsMenu] android.view.Menu: " + menu);

        if (!callbackPrepareOptionsMenu(mMenu)) {
            return false;
        }

        if (isReservingOverflow()) {
            return false;
        }

        if (mNativeItemMap == null) {
            mNativeItemMap = new HashMap<android.view.MenuItem, MenuItemImpl>();
        } else {
            mNativeItemMap.clear();
        }

        if (mMenu == null) {
            return false;
        }

        return mMenu.bindNativeOverflow(menu, mNativeItemListener, mNativeItemMap);
    }

    @Override
    public boolean dispatchOptionsItemSelected(android.view.MenuItem item) {
        throw new IllegalStateException("Native callback invoked. Create a test case and report!");
    }

    @Override
    public boolean dispatchMenuOpened(int featureId, android.view.Menu menu) {
        if (DEBUG) Log.d(TAG, "[dispatchMenuOpened] featureId: " + featureId + ", menu: " + menu);

        if (featureId == Window.FEATURE_ACTION_BAR || featureId == Window.FEATURE_OPTIONS_PANEL) {
            if (mActionBar != null) {
                mActionBar.dispatchMenuVisibilityChanged(true);
            }
            return true;
        }

        return false;
    }

    @Override
    public void dispatchPanelClosed(int featureId, android.view.Menu menu){
        if (DEBUG) Log.d(TAG, "[dispatchPanelClosed] featureId: " + featureId + ", menu: " + menu);

        if (featureId == Window.FEATURE_ACTION_BAR || featureId == Window.FEATURE_OPTIONS_PANEL) {
            if (mActionBar != null) {
                mActionBar.dispatchMenuVisibilityChanged(false);
            }
        }
    }

    @Override
    public void dispatchTitleChanged(CharSequence title, int color) {
        if (DEBUG) Log.d(TAG, "[dispatchTitleChanged] title: " + title + ", color: " + color);

        if (mIsDelegate && !mIsTitleReady) {
            return;
        }
        if (mActionBarView != null) {
            mActionBarView.setWindowTitle(title);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (DEBUG) Log.d(TAG, "[dispatchKeyEvent] event: " + event);

        final int keyCode = event.getKeyCode();

        // Not handled by the view hierarchy, does the action bar want it
        // to cancel out of something special?
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final int action = event.getAction();
            // Back cancels action modes first.
            //if (mActionMode != null) {
            //    if (action == KeyEvent.ACTION_UP) {
            //        mActionMode.finish();
            //    }
            //    if (DEBUG) Log.d(TAG, "[dispatchKeyEvent] returning true");
            //    return true;
            //}

            // Next collapse any expanded action views.
            //if (mActionBar != null && mActionBarView.hasExpandedActionView()) {
            //    if (action == KeyEvent.ACTION_UP) {
            //        mActionBarView.collapseActionView();
            //    }
            //    if (DEBUG) Log.d(TAG, "[dispatchKeyEvent] returning true");
            //    return true;
            //}
        }

        if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP && isReservingOverflow()) {
            //if (mActionMode == null) {
                if (mActionBarView.isOverflowMenuShowing()) {
                    mActionBarView.hideOverflowMenu();
                } else {
                    mActionBarView.showOverflowMenu();
                }
            //}
            if (DEBUG) Log.d(TAG, "[dispatchKeyEvent] returning true");
            return true;
        }

        if (DEBUG) Log.d(TAG, "[dispatchKeyEvent] returning false");
        return false;
    }

    private void installDecor() {
        if (DEBUG) Log.d(TAG, "[installDecor]");

        if (mDecor == null) {
            mDecor = (ViewGroup)mActivity.getWindow().getDecorView().findViewById(android.R.id.content);
        }
        if (mContentParent == null) {
            mContentParent = generateLayout();
            mActionBarView = (ActionBarLimitedView)mDecor.findViewById(R.id.abs__action_bar);
            if (mActionBarView != null) {
                mActionBarView.setWindowCallback(mWindowCallback);
                if (mActionBarView.getTitle() == null) {
                    mActionBarView.setWindowTitle(mActivity.getTitle());
                }
                if (hasFeature(Window.FEATURE_PROGRESS)) {
                    //mActionBarView.initProgress();
                }
                if (hasFeature(Window.FEATURE_INDETERMINATE_PROGRESS)) {
                    //mActionBarView.initIndeterminateProgress();
                }

                //Since we don't require onCreate dispatching, parse for uiOptions here
                mUiOptions = 0;//loadUiOptionsFromManifest(mActivity);

                boolean splitActionBar = false;
                final boolean splitWhenNarrow = (mUiOptions & ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW) != 0;
                if (splitWhenNarrow) {
                    splitActionBar = getResources_getBoolean(mActivity, R.bool.abs__split_action_bar_is_narrow);
                } else {
                    splitActionBar = mActivity.getTheme()
                            .obtainStyledAttributes(R.styleable.SherlockTheme)
                            .getBoolean(R.styleable.SherlockTheme_windowSplitActionBar, false);
                }
                final ActionBarLimitedContainer splitView = (ActionBarLimitedContainer)mDecor.findViewById(R.id.abs__split_action_bar);
                if (splitView != null) {
                    mActionBarView.setSplitView(splitView);
                    mActionBarView.setSplitActionBar(splitActionBar);
                    mActionBarView.setSplitWhenNarrow(splitWhenNarrow);

                    //mActionModeView = (ActionBarContextView)mDecor.findViewById(R.id.abs__action_context_bar);
                    //mActionModeView.setSplitView(splitView);
                    //mActionModeView.setSplitActionBar(splitActionBar);
                    //mActionModeView.setSplitWhenNarrow(splitWhenNarrow);
                } else if (splitActionBar) {
                    Log.e(TAG, "Requested split action bar with incompatible window decor! Ignoring request.");
                }

                // Post the panel invalidate for later; avoid application onCreateOptionsMenu
                // being called in the middle of onCreate or similar.
                mDecor.post(new Runnable() {
                    @Override
                    public void run() {
                        //Invalidate if the panel menu hasn't been created before this.
                        if (mMenu == null) {
                            dispatchInvalidateOptionsMenu();
                        }
                    }
                });
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void setProgressBarVisibility(boolean visible) {
        setFeatureInt(Window.FEATURE_PROGRESS, visible ? Window.PROGRESS_VISIBILITY_ON :
            Window.PROGRESS_VISIBILITY_OFF);
    }

    @Override
    public void setProgressBarIndeterminateVisibility(boolean visible) {
        setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                visible ? Window.PROGRESS_VISIBILITY_ON : Window.PROGRESS_VISIBILITY_OFF);
    }

    @Override
    public void setProgressBarIndeterminate(boolean indeterminate) {
        setFeatureInt(Window.FEATURE_PROGRESS,
                indeterminate ? Window.PROGRESS_INDETERMINATE_ON : Window.PROGRESS_INDETERMINATE_OFF);
    }

    @Override
    public void setProgress(int progress) {
        setFeatureInt(Window.FEATURE_PROGRESS, progress + Window.PROGRESS_START);
    }

    @Override
    public void setSecondaryProgress(int secondaryProgress) {
        setFeatureInt(Window.FEATURE_PROGRESS,
                secondaryProgress + Window.PROGRESS_SECONDARY_START);
    }

    ///////////////////////////////////////////////////////////////////////////
    // XXX
    ///////////////////////////////////////////////////////////////////////////

    private int getFeatures() {
        if (DEBUG) Log.d(TAG, "[getFeatures]");

        return mFeatures;
    }

    @Override
    public boolean hasFeature(int featureId) {
        if (DEBUG) Log.d(TAG, "[hasFeature] featureId: " + featureId);

        return (mFeatures & (1 << featureId)) != 0;
    }

    @Override
    public boolean requestFeature(int featureId) {
        if (DEBUG) Log.d(TAG, "[requestFeature] featureId: " + featureId);

        if (mContentParent != null) {
            throw new AndroidRuntimeException("requestFeature() must be called before adding content");
        }

        switch (featureId) {
            case Window.FEATURE_ACTION_BAR:
            case Window.FEATURE_ACTION_BAR_OVERLAY:
            case Window.FEATURE_ACTION_MODE_OVERLAY:
            case Window.FEATURE_INDETERMINATE_PROGRESS:
            case Window.FEATURE_NO_TITLE:
            case Window.FEATURE_PROGRESS:
                mFeatures |= (1 << featureId);
                return true;

            default:
                return false;
        }
    }

    @Override
    public void setUiOptions(int uiOptions) {
        if (DEBUG) Log.d(TAG, "[setUiOptions] uiOptions: " + uiOptions);

        mUiOptions = uiOptions;
    }

    @Override
    public void setUiOptions(int uiOptions, int mask) {
        if (DEBUG) Log.d(TAG, "[setUiOptions] uiOptions: " + uiOptions + ", mask: " + mask);

        mUiOptions = (mUiOptions & ~mask) | (uiOptions & mask);
    }

    @Override
    public void setContentView(int layoutResId) {
        if (DEBUG) Log.d(TAG, "[setContentView] layoutResId: " + layoutResId);

        if (mContentParent == null) {
            installDecor();
        } else {
            mContentParent.removeAllViews();
        }
        mActivity.getLayoutInflater().inflate(layoutResId, mContentParent);

        android.view.Window.Callback callback = mActivity.getWindow().getCallback();
        if (callback != null) {
            callback.onContentChanged();
        }

        initActionBar();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (DEBUG) Log.d(TAG, "[setContentView] view: " + view + ", params: " + params);

        if (mContentParent == null) {
            installDecor();
        } else {
            mContentParent.removeAllViews();
        }
        mContentParent.addView(view, params);

        android.view.Window.Callback callback = mActivity.getWindow().getCallback();
        if (callback != null) {
            callback.onContentChanged();
        }

        initActionBar();
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (DEBUG) Log.d(TAG, "[addContentView] view: " + view + ", params: " + params);

        if (mContentParent == null) {
            installDecor();
        }
        mContentParent.addView(view, params);

        initActionBar();
    }

    @Override
    public void setTitle(CharSequence title) {
        if (DEBUG) Log.d(TAG, "[setTitle] title: " + title);

        dispatchTitleChanged(title, 0);
    }

    private void setFeatureInt(int featureId, int value) {
        updateInt(featureId, value, false);
    }

    private void updateInt(int featureId, int value, boolean fromResume) {
        // Do nothing if the decor is not yet installed... an update will
        // need to be forced when we eventually become active.
        if (mContentParent == null) {
            return;
        }

        final int featureMask = 1 << featureId;

        if ((getFeatures() & featureMask) == 0 && !fromResume) {
            return;
        }

        onIntChanged(featureId, value);
    }

    private void onIntChanged(int featureId, int value) {
        if (featureId == Window.FEATURE_PROGRESS || featureId == Window.FEATURE_INDETERMINATE_PROGRESS) {
            //updateProgressBars(value);
        }
    }

    private ViewGroup generateLayout() {
        if (DEBUG) Log.d(TAG, "[generateLayout]");

        // Apply data from current theme.

        TypedArray a = mActivity.getTheme().obtainStyledAttributes(R.styleable.SherlockTheme);
        if (!a.hasValue(R.styleable.SherlockTheme_windowActionBar)) {
            throw new IllegalStateException("You must use Theme.Sherlock, Theme.Sherlock.Light, Theme.Sherlock.Light.DarkActionBar, or a derivative.");
        }

        if (a.getBoolean(R.styleable.SherlockTheme_windowNoTitle, false)) {
            requestFeature(Window.FEATURE_NO_TITLE);
        } else if (a.getBoolean(R.styleable.SherlockTheme_windowActionBar, false)) {
            // Don't allow an action bar if there is no title.
            requestFeature(Window.FEATURE_ACTION_BAR);
        }

        if (a.getBoolean(R.styleable.SherlockTheme_windowActionBarOverlay, false)) {
            requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        }

        if (a.getBoolean(R.styleable.SherlockTheme_windowActionModeOverlay, false)) {
            requestFeature(Window.FEATURE_ACTION_MODE_OVERLAY);
        }

        a.recycle();

        int layoutResource;
        if (hasFeature(Window.FEATURE_ACTION_BAR)) {
            //if (hasFeature(Window.FEATURE_ACTION_BAR_OVERLAY)) {
            //    layoutResource = R.layout.abs__screen_action_bar_overlay;
            //} else {
                layoutResource = R.layout.abs__screen_action_bar_limited;
            //}
        //} else if (hasFeature(Window.FEATURE_ACTION_MODE_OVERLAY)) {
        //    layoutResource = R.layout.abs__screen_simple_overlay_action_mode;
        } else {
            layoutResource = R.layout.abs__screen_simple;
        }

        View in = mActivity.getLayoutInflater().inflate(layoutResource, null);
        mDecor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        ViewGroup contentParent = (ViewGroup)mDecor.findViewById(R.id.abs__content);
        if (contentParent == null) {
            throw new RuntimeException("Couldn't find content container view");
        }

        //Make our new child the true content view (for fragments). VERY VOLATILE!
        mDecor.setId(View.NO_ID);
        contentParent.setId(android.R.id.content);

        if (hasFeature(Window.FEATURE_INDETERMINATE_PROGRESS)) {
            //IcsProgressBar progress = getCircularProgressBar(false);
            //if (progress != null) {
            //    progress.setIndeterminate(true);
            //}
        }

        return contentParent;
    }

    private void reopenMenu(boolean toggleMenuMode) {
        if (mActionBarView != null && mActionBarView.isOverflowReserved()) {
            if (!mActionBarView.isOverflowMenuShowing() || !toggleMenuMode) {
                if (mActionBarView.getVisibility() == View.VISIBLE) {
                    if (callbackPrepareOptionsMenu(mMenu)) {
                        mActionBarView.showOverflowMenu();
                    }
                }
            } else {
                mActionBarView.hideOverflowMenu();
            }
            return;
        }
    }
}
