package me.aap.fermata.addon.web;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;
import static android.view.MotionEvent.ACTION_UP;
import static androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING;
import static androidx.webkit.WebViewFeature.FORCE_DARK;
import static androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EDIT;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EVENT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.text.Editable;
import android.util.AttributeSet;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.TextChangedListener;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebView extends WebView
		implements TextChangedListener, TextView.OnEditorActionListener, PreferenceStore.Listener,
		MainActivityListener {
	private static final ExecutorService COOKIE_IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "HomeCar-CookieIO");
		t.setDaemon(true);
		return t;
	});
	private final boolean isCar;
	private WebBrowserAddon addon;
	private FermataWebClient webClient;
	private FermataChromeClient chrome;

	public FermataWebView(Context context) {
		this(context, null);
	}

	public FermataWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	public FermataWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void init(WebBrowserAddon addon, FermataWebClient webClient,
									 FermataChromeClient chromeClient) {
		this.addon = addon;
		this.webClient = webClient;
		setWebViewClient(webClient);
		setWebChromeClient(chromeClient);
		WebSettings s = getSettings();
		s.setSupportZoom(true);
		s.setBuiltInZoomControls(true);
		s.setDisplayZoomControls(false);
		s.setDatabaseEnabled(true);
		s.setDomStorageEnabled(true);
		// Chromium's normal HTTP cache; do not force a full network reload.
		s.setCacheMode(WebSettings.LOAD_DEFAULT);
		s.setAllowFileAccess(false);
		s.setAllowContentAccess(false);
		s.setLoadWithOverviewMode(true);
		s.setJavaScriptEnabled(true);
		s.setMediaPlaybackRequiresUserGesture(false);
		s.setJavaScriptCanOpenWindowsAutomatically(false);

		addJavascriptInterface(createJsInterface(), FermataJsInterface.NAME);
		CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

		addon.getPreferenceStore().addBroadcastListener(this);
		getActivity().onSuccess(a -> a.addBroadcastListener(this));

		setDesktopMode(addon, false);
		setForceDark(addon, false);
		if (isCar()) {
			setFocusable(false);
			setFocusableInTouchMode(false);
		}
	}

	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		if (!BuildConfig.AUTO) super.onWindowVisibilityChanged(visibility);
		else if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		if (prefs.contains(a.getDesktopVersionPref())) {
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentPref())) {
			UserAgent.ua = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentDesktopPref())) {
			UserAgent.uaDesktop = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getForceDarkPref())) {
			setForceDark(addon, true);
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			getAddon().getPreferenceStore().removeBroadcastListener(this);
		}
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		FermataChromeClient c = getWebChromeClient();
		if ((c != null) && c.isFullScreen()) getActivity().onSuccess(a -> c.setFullScreen(a, true));
	}

	private void setDesktopMode(WebBrowserAddon a, boolean reload) {
		if (getClass() != FermataWebView.class) return;

		WebSettings s = getSettings();
		boolean v = a.getPreferenceStore().getBooleanPref(a.getDesktopVersionPref());
		String ua = v ? UserAgent.getUaDesktop(s, a) : UserAgent.getUa(s, a);
		s.setUseWideViewPort(v);

		try {
			Log.d("Setting User-Agent to " + ua);
			s.setUserAgentString(ua);
		} catch (Exception ex) {
			Log.e(ex, "Invalid User-Agent: ", ua);
			String msg = ex.getLocalizedMessage();
			if (msg == null) msg = "Invalid User-Agent: " + ua;
			UiUtils.showAlert(getContext(), msg);
		}

		if (reload) reload();
	}

	@SuppressWarnings("deprecation")
	private void setForceDark(WebBrowserAddon a, boolean reload) {
		if ((VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) &&
				(WebViewFeature.isFeatureSupported(ALGORITHMIC_DARKENING))) {
			boolean dark = a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark());
			WebSettingsCompat.setAlgorithmicDarkeningAllowed(getSettings(), dark);
			if (reload) reload();
		} else if (WebViewFeature.isFeatureSupported(FORCE_DARK)) {
			int force;
			int strategy;
			if (a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark())) {
				force = WebSettingsCompat.FORCE_DARK_ON;
				strategy = WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING;
			} else {
				force = WebSettingsCompat.FORCE_DARK_OFF;
				strategy = WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY;
			}
			WebSettingsCompat.setForceDark(getSettings(), force);
			if (WebViewFeature.isFeatureSupported(FORCE_DARK_STRATEGY))
				WebSettingsCompat.setForceDarkStrategy(getSettings(), strategy);
			if (reload) reload();
		}
	}

	private boolean isDarkPhoneTheme() {
		int mode = getResources().getConfiguration().uiMode;
		return (mode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
	}

	protected FermataJsInterface createJsInterface() {
		return new FermataJsInterface(this);
	}

	protected boolean isCar() {
		return BuildConfig.AUTO && isCar;
	}

	public WebBrowserAddon getAddon() {
		return addon;
	}

	@NonNull
	@Override
	public FermataWebClient getWebViewClient() {
		return webClient;
	}

	public void setWebChromeClient(FermataChromeClient chrome) {
		this.chrome = chrome;
		super.setWebChromeClient(chrome);
	}

	@Nullable
	@Override
	public FermataChromeClient getWebChromeClient() {
		return chrome;
	}

	protected void pageLoaded(String uri) {
		addFocusHighlight();
		getAddon().setLastUrl(uri);
		getActivity().onSuccess(a -> {
			ActivityFragment f = a.getActiveFragment();
			if (f == null) return;

			ToolBarView.Mediator m = f.getToolBarMediator();

			if (m instanceof WebToolBarMediator wm) {
				ToolBarView tb = a.getToolBar();
				wm.setAddress(tb, uri);
				wm.setButtonsVisibility(tb, canGoBack(), canGoForward());
			}

			// flush() may perform synchronous I/O: keep it off the render thread.
			COOKIE_IO.execute(() -> CookieManager.getInstance().flush());
		});
	}

	protected void addFocusHighlight() {
		if (!isCar()) {
			evaluateJavascript("""
					(function() {
					  var style = document.createElement('style');
					  style.innerHTML = ':focus {outline: 2px solid blue !important; border-radius: 5px;}';
					  document.head.appendChild(style);
					})()""", null);
			return;
		}
		evaluateJavascript(KNOB_FOCUS_JS, null);
	}

	/**
	 * Moves a visible ring through actionable controls, including ones inside
	 * open shadow roots. Home Assistant dashboards keep their buttons there,
	 * so a plain :focus rule never lands on them.
	 */
	private static final String KNOB_FOCUS_JS = """
			(function() {
			  if (window.homeCarKnob) return;
			  var ring = document.createElement('div');
			  ring.id = 'homecar-knob-ring';
			  ring.setAttribute('aria-hidden', 'true');
			  ring.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;box-sizing:border-box;border:3px solid #1a73e8;border-radius:10px;box-shadow:0 0 0 2px #fff,0 0 0 5px rgba(0,0,0,.55);display:none;';
			  (document.documentElement || document.body).appendChild(ring);
			  var current = null;
			  var roles = {button:1,switch:1,checkbox:1,link:1,tab:1,menuitem:1,option:1,radio:1,slider:1};
			  function tagOf(el) { return (el.tagName || '').toUpperCase(); }
			  function actionable(el) {
			    if (!el || el.nodeType !== 1 || el === ring) return false;
			    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
			    if (el.getAttribute('aria-hidden') === 'true') return false;
			    var tag = tagOf(el);
			    if (tag === 'INPUT') {
			      var type = (el.type || '').toLowerCase();
			      if (type === 'hidden' || type === 'file') return false;
			      return true;
			    }
			    if (tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA' || tag === 'SUMMARY') return true;
			    if (tag === 'A' && el.hasAttribute('href')) return true;
			    var role = el.getAttribute('role');
			    if (role && roles[role]) return true;
			    if (el.hasAttribute('tabindex') && el.tabIndex >= 0) return true;
			    if (el.hasAttribute('onclick')) return true;
			    if (/button|switch|toggle|slider|checkbox/i.test(tag)) return true;
			    return false;
			  }
			  function shown(el) {
			    var s = getComputedStyle(el);
			    if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) === 0) return false;
			    var r = el.getBoundingClientRect();
			    return r.width >= 8 && r.height >= 8;
			  }
			  function inside(ancestor, el) {
			    var n = el;
			    while (n) {
			      if (n === ancestor) return true;
			      if (n.parentElement) n = n.parentElement;
			      else {
			        var root = n.getRootNode ? n.getRootNode() : null;
			        n = (root && root.host) ? root.host : null;
			      }
			    }
			    return false;
			  }
			  function walk(node, out) {
			    if (!node) return;
			    var kids = node.children;
			    if (!kids) return;
			    for (var i = 0; i < kids.length; i++) {
			      var el = kids[i];
			      if (el.shadowRoot) walk(el.shadowRoot, out);
			      if (actionable(el) && shown(el)) out.push(el);
			      walk(el, out);
			    }
			  }
			  function collect() {
			    var raw = [];
			    walk(document.body || document.documentElement, raw);
			    var kept = [];
			    for (var i = 0; i < raw.length; i++) {
			      var el = raw[i];
			      var nested = false;
			      for (var j = 0; j < raw.length; j++) {
			        if (raw[j] !== el && inside(el, raw[j])) { nested = true; break; }
			      }
			      if (!nested) kept.push(el);
			    }
			    kept.sort(function(a, b) {
			      var ra = a.getBoundingClientRect();
			      var rb = b.getBoundingClientRect();
			      var dy = ra.top - rb.top;
			      if (Math.abs(dy) > 8) return dy;
			      return ra.left - rb.left;
			    });
			    return kept;
			  }
			  function place(el) {
			    if (!el || !el.isConnected) { ring.style.display = 'none'; current = null; return; }
			    current = el;
			    try { el.focus({preventScroll:true}); } catch (e) { try { el.focus(); } catch (e2) {} }
			    try { el.scrollIntoView({block:'nearest', inline:'nearest'}); } catch (e3) { el.scrollIntoView(false); }
			    var r = el.getBoundingClientRect();
			    ring.style.display = 'block';
			    ring.style.left = Math.round(r.left - 4) + 'px';
			    ring.style.top = Math.round(r.top - 4) + 'px';
			    ring.style.width = Math.round(r.width + 8) + 'px';
			    ring.style.height = Math.round(r.height + 8) + 'px';
			  }
			  function move(dir) {
			    var items = collect();
			    if (!items.length) { ring.style.display = 'none'; current = null; return; }
			    var start = items.indexOf(current);
			    var next = start < 0 ? (dir > 0 ? 0 : items.length - 1) : (start + dir + items.length) % items.length;
			    place(items[next]);
			  }
			  function activate() {
			    var items = collect();
			    var el = (current && current.isConnected) ? current : (items.length ? items[0] : null);
			    if (!el) return;
			    place(el);
			    el.click();
			  }
			  var reported = [];
			  var publishTimer = 0;
			  function publish() {
			    var items = collect();
			    reported = items;
			    var parts = [];
			    for (var i = 0; i < items.length; i++) {
			      var r = items[i].getBoundingClientRect();
			      parts.push(Math.round(r.left) + ',' + Math.round(r.top) + ',' + Math.round(r.width) + ',' + Math.round(r.height));
			    }
			    if (window.Fermata && window.Fermata.knobTargets) {
			      window.Fermata.knobTargets(window.innerWidth | 0, window.innerHeight | 0, parts.join(';'));
			    }
			  }
			  function schedule() {
			    if (publishTimer) return;
			    publishTimer = setTimeout(function() { publishTimer = 0; publish(); }, 200);
			  }
			  function reveal(i) {
			    var el = reported[i];
			    if (!el || !el.isConnected) return;
			    current = el;
			    try { el.scrollIntoView({block:'nearest', inline:'nearest'}); } catch (e) { el.scrollIntoView(false); }
			    schedule();
			  }
			  function clickIndex(i) {
			    var el = reported[i];
			    if (!el || !el.isConnected) { publish(); el = reported[i]; }
			    if (!el) return;
			    place(el);
			    el.click();
			  }
			  window.homeCarKnob = {
			    next: function() { move(1); },
			    prev: function() { move(-1); },
			    activate: activate,
			    show: reveal,
			    click: clickIndex,
			    hide: function() {
			      ring.style.display = 'none';
			      current = null;
			      try {
			        var active = document.activeElement;
			        if (active && active !== document.body && active.blur) active.blur();
			      } catch (e) {}
			    }
			  };
			  window.addEventListener('scroll', function() { if (current) place(current); schedule(); }, true);
			  window.addEventListener('resize', schedule);
			  if (window.MutationObserver && document.body) {
			    new MutationObserver(schedule).observe(document.body, {subtree:true, childList:true, attributes:true});
			  }
			  publish();
			})();
			""";

	public static final String KNOB_TARGET_TAG = "homecar-knob";
	/** Tilt left the screen, so the outline stays off until the next rotation. */
	private boolean knobSelectionHidden;
	private boolean knobSelectionRearm;

	/**
	 * Android Auto rotates between focusable views. The WebView is only one,
	 * so each dashboard control gets its own transparent, focusable target.
	 */
	public void setKnobTargets(int innerW, int innerH, String packed) {
		if (!isCar() || (innerW <= 0) || (innerH <= 0)) return;
		if (getWidth() <= 0) {
			post(() -> setKnobTargets(innerW, innerH, packed));
			return;
		}
		ViewParent parent = getParent();
		if (!(parent instanceof ViewGroup group)) return;
		View containerView = group.findViewById(R.id.knobTargets);
		if (!(containerView instanceof FrameLayout container)) return;
		container.setTouchscreenBlocksFocus(false);
		container.setOnGenericMotionListener((v, event) -> moveKnobOnMotion(container, event));
		group.setTouchscreenBlocksFocus(false);
		group.setOnGenericMotionListener((v, event) -> moveKnobOnMotion(container, event));

		float sx = getWidth() / (float) innerW;
		float sy = getHeight() / (float) innerH;
		String[] rows = (packed == null || packed.isEmpty()) ? new String[0] : packed.split(";");
		int focused = -1;
		View currentFocus = container.findFocus();
		if (currentFocus != null) focused = container.indexOfChild(currentFocus);

		if (container.getChildCount() != rows.length) {
			container.removeAllViews();
			for (int i = 0; i < rows.length; i++) {
				View target = new View(getContext());
				target.setTag(KNOB_TARGET_TAG);
				target.setId(View.generateViewId());
				target.setFocusable(true);
				target.setFocusableInTouchMode(true);
				target.setClickable(true);
				target.setContentDescription("Dashboard control");
				target.setDefaultFocusHighlightEnabled(false);
				int index = i;
				target.setOnFocusChangeListener((v, hasFocus) -> {
					if (!hasFocus || (knobSelectionHidden && !knobSelectionRearm)) {
						v.setBackground(null);
						return;
					}
					knobSelectionHidden = false;
					v.setBackground(knobFocusDrawable());
					evaluateJavascript("window.homeCarKnob&&window.homeCarKnob.show(" + index + ")",
							null);
				});
				target.setOnClickListener(v -> evaluateJavascript(
						"window.homeCarKnob&&window.homeCarKnob.click(" + index + ")", null));
				container.addView(target, new FrameLayout.LayoutParams(1, 1));
			}
			for (int i = 0; i < rows.length; i++) {
				View cur = container.getChildAt(i);
				View next = container.getChildAt((i + 1) % rows.length);
				cur.setNextFocusForwardId(next.getId());
				cur.setNextFocusDownId(View.NO_ID);
				cur.setNextFocusRightId(View.NO_ID);
				cur.setNextFocusUpId(View.NO_ID);
				cur.setNextFocusLeftId(View.NO_ID);
			}
			if ((focused >= 0) && (focused < container.getChildCount())) {
				container.getChildAt(focused).requestFocus();
			}
		}

		for (int i = 0; i < rows.length; i++) {
			String[] part = rows[i].split(",");
			if (part.length != 4) continue;
			int left;
			int top;
			int width;
			int height;
			try {
				left = Math.round(Integer.parseInt(part[0]) * sx);
				top = Math.round(Integer.parseInt(part[1]) * sy);
				width = Math.max(1, Math.round(Integer.parseInt(part[2]) * sx));
				height = Math.max(1, Math.round(Integer.parseInt(part[3]) * sy));
			} catch (NumberFormatException ex) {
				continue;
			}
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
			lp.leftMargin = left;
			lp.topMargin = top;
			container.getChildAt(i).setLayoutParams(lp);
		}
	}

	private boolean moveKnobOnMotion(ViewGroup container, MotionEvent event) {
		float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
		if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
		if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
		if (delta == 0f) return false;
		knobSelectionHidden = false;
		int count = container.getChildCount();
		if (count == 0) return false;
		View focused = container.findFocus();
		int index = (focused == null) ? -1 : container.indexOfChild(focused);
		int next = (index < 0) ? ((delta > 0f) ? 0 : count - 1) :
				Math.floorMod(index + ((delta > 0f) ? 1 : -1), count);
		container.getChildAt(next).requestFocusFromTouch();
		return true;
	}

	private Drawable knobFocusDrawable() {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(Color.TRANSPARENT);
		int stroke = Math.max(3, (int) (3 * getResources().getDisplayMetrics().density));
		drawable.setStroke(stroke, 0xFF1A73E8);
		drawable.setCornerRadius(stroke * 3f);
		return drawable;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (!isCar()) return super.dispatchKeyEvent(event);
		int direction = knobDirection(event);
		if (direction != 0) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) moveKnobFocus(direction);
			return true;
		}
		if (isKnobActivate(event.getKeyCode())) {
			if (event.getAction() == KeyEvent.ACTION_UP) activateKnobFocus();
			return true;
		}
		if (isKnobNudge(event.getKeyCode())) {
			if (event.getAction() == KeyEvent.ACTION_DOWN) hideKnobSelection();
			return false;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (isCar() && isRotaryMotion(event)) {
			float delta = event.getAxisValue(MotionEvent.AXIS_SCROLL);
			if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
			if (delta == 0f) delta = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
			if (delta != 0f) {
				moveKnobFocus(delta > 0f ? 1 : -1);
				return true;
			}
			Log.i("Unmapped rotary motion source=", event.getSource(), " action=",
					event.getAction());
		}
		return super.onGenericMotionEvent(event);
	}

	/**
	 * Drop the outline when the knob tilts to another Android Auto pane.
	 * A focus change in this same key event must not draw it again; the next
	 * rotation may.
	 */
	private void hideKnobSelection() {
		knobSelectionHidden = true;
		knobSelectionRearm = false;
		ViewParent parent = getParent();
		if (parent instanceof ViewGroup group) {
			View containerView = group.findViewById(R.id.knobTargets);
			if (containerView instanceof ViewGroup container) {
				for (int i = 0; i < container.getChildCount(); i++) {
					container.getChildAt(i).setBackground(null);
				}
				container.clearFocus();
			}
		}
		evaluateJavascript("window.homeCarKnob&&window.homeCarKnob.hide()", null);
		post(() -> {
			if (knobSelectionHidden) knobSelectionRearm = true;
		});
	}

	private void moveKnobFocus(int direction) {
		evaluateJavascript(direction > 0 ? "window.homeCarKnob&&window.homeCarKnob.next()" :
				"window.homeCarKnob&&window.homeCarKnob.prev()", null);
	}

	private void activateKnobFocus() {
		evaluateJavascript("window.homeCarKnob&&window.homeCarKnob.activate()", null);
	}

	private static int knobDirection(KeyEvent event) {
		int code = event.getKeyCode();
		if (code == KeyEvent.KEYCODE_TAB) return event.isShiftPressed() ? -1 : 1;
		if (code == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) return -1;
		if (code == KeyEvent.KEYCODE_NAVIGATE_NEXT) return 1;
		return 0;
	}

	private static boolean isKnobActivate(int keyCode) {
		return (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) || (keyCode == KeyEvent.KEYCODE_ENTER) ||
				(keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER);
	}

	private static boolean isKnobNudge(int keyCode) {
		return switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
					KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP_LEFT,
					KeyEvent.KEYCODE_DPAD_UP_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
					KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
					KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
					KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> true;
			default -> false;
		};
	}

	private static boolean isRotaryMotion(MotionEvent event) {
		return (event.getSource() & InputDevice.SOURCE_ROTARY_ENCODER) ==
				InputDevice.SOURCE_ROTARY_ENCODER;
	}

	protected boolean requestFullScreen() {
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isCar()) {
			if (event.getAction() == ACTION_UP) checkTextInput();
		}
		return super.onTouchEvent(event);
	}

	private void checkTextInput() {
		if (!BuildConfig.AUTO || isKeyboardActive()) return;

		Log.d("checkTextInput");
		loadUrl("javascript:\n" + "function checkInput() {\n" + "  var e =  document.activeElement;" +
				"\n" + "  if (e == null) return;\n" + "  if (e instanceof HTMLInputElement) {\n" + "    " +
				JS_EVENT + '(' + JS_EDIT + ", e.value);\n" +
				"  } else if(e.getAttribute('contenteditable') == 'true') {\n" + "    " + JS_EVENT + '(' +
				JS_EDIT + ", e.innerText);\n" + "  }\n" + "}\n" + "setTimeout(checkInput, 500);");
	}

	private void setTextInput(CharSequence text) {
		if (!BuildConfig.AUTO) return;

		// Never log or interpolate unescaped text: this can contain passwords.
		String quotedText = JSONObject.quote(text.toString());
		loadUrl(
				"javascript:\n" + "var e =  document.activeElement;\n" + "var text = " + quotedText + ";\n" +
						"if (e.isContentEditable) e.innerText = text;\n" + "else e.value = text;\n" +
						"e.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));\n" +
						"e.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true }));\n" +
						"e.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: " +
						"'insertText' }));\n" +
						"e.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));\n" +
						"e.dispatchEvent(new Event('change', { bubbles: true }));");
	}


	protected void submitForm() {
		if (!BuildConfig.AUTO) return;
		loadUrl("""
				javascript:
				var ae = document.activeElement;
				if (ae.form != null) {
				  ae.form.submit();
				} else {
				  var e = new KeyboardEvent('keydown',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				  e = new KeyboardEvent('keyup',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				}""");
	}

	public void showKeyboard(String text) {
		if (!BuildConfig.AUTO) return;

		getActivity().onSuccess(a -> {
			EditText et = a.getAppActivity().startInput(this);
			if (et == null) return;

			if (text != null) {
				et.setText(text);
				et.setSelection(et.getText().length());
			}

			et.setOnEditorActionListener(this);
		});
	}

	public void hideKeyboard() {
		if (!BuildConfig.AUTO) return;
		getActivity().onSuccess(a -> a.getAppActivity().stopInput());
	}

	private boolean isKeyboardActive() {
		if (!BuildConfig.AUTO) return false;

		FermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();
		return (a != null) && a.isInputActive();
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (BuildConfig.AUTO) setTextInput(s);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, @Nullable KeyEvent event) {
		if (!BuildConfig.AUTO) return false;

		switch (actionId) {
			case EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
					EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
				submitForm();
				hideKeyboard();
			}
		}

		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		FermataChromeClient chrome = getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.onTouchEvent(this, ev);
		} else if (BuildConfig.AUTO) {
			FermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();

			if ((a != null) && a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	static final class UserAgent {
		private static final Pattern pattern =
				Pattern.compile(".+ AppleWebKit/(\\S+) .+ Chrome/(\\S+) .+");
		static String ua;
		static String uaDesktop;

		static String getUa(WebSettings s, WebBrowserAddon a) {
			if (ua != null) return ua;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String av;
				if (VERSION.SDK_INT >= VERSION_CODES.R) av = VERSION.RELEASE_OR_CODENAME;
				else av = VERSION.RELEASE;
				String wv = m.group(1);
				String cv = m.group(2);
				UserAgent.ua = a.getUserAgent().replace("{ANDROID_VERSION}", av)
						.replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
				UserAgent.ua = normalize(UserAgent.ua);
				if (UserAgent.ua.isEmpty()) UserAgent.ua = ua;
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				UserAgent.ua = ua;
			}

			return UserAgent.ua;
		}

		static String getUaDesktop(WebSettings s, WebBrowserAddon a) {
			if (uaDesktop != null) return uaDesktop;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String wv = m.group(1);
				String cv = m.group(2);
				uaDesktop = a.getUserAgentDesktop().replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				int i1 = ua.indexOf('(') + 1;
				int i2 = ua.indexOf(')', i1);
				uaDesktop = ua.substring(0, i1) + "X11; Linux x86_64" +
						ua.substring(i2).replace(" Mobile ", " ").replaceFirst(" Version/\\d+\\.\\d+ ", " ");
			}

			return uaDesktop = normalize(uaDesktop);
		}

		private static String normalize(String ua) {
			try (SharedTextBuilder b = SharedTextBuilder.get()) {
				int cut = 0;
				boolean changed = false;

				for (int i = 0, n = ua.length(); i < n; i++) {
					char c = ua.charAt(i);

					if (c <= ' ') {
						if ((b.length() == 0) || (ua.charAt(i - 1) == ' ')) {
							changed = true;
							continue;
						} else if (c != ' ') {
							b.append(' ');
							changed = true;
							continue;
						}
					}

					b.append(c);
				}

				for (int i = b.length() - 1; i >= 0; i--) {
					if (b.charAt(i) == ' ') cut++;
					else break;
				}

				if (cut != 0) {
					changed = true;
					b.setLength(b.length() - cut);
				}

				return changed ? b.toString() : ua;
			}
		}
	}
}
