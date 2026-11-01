package me.aap.fermata.auto;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.R;
import me.aap.fermata.action.Key;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	static FermataMediaServiceConnection service;
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate =
			(FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private TextWatcher textWatcher;

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MainActivityDelegate.attachBaseContext(base));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		initCarActivity(this);
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onCreate(savedInstanceState, s);
		} else {
			delegate = FermataMediaServiceConnection.connect(this).main()
					.onFailure(err -> showAlert(getContext(), String.valueOf(err))).map(c -> {
						service = c;
						return onCreate(savedInstanceState, c);
					});
		}
	}

	static void initCarActivity(CarActivity a) {
		a.setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = a.getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	private MainActivityDelegate onCreate(Bundle state, FermataMediaServiceConnection s) {
		MainActivityDelegate d = new MainActivityDelegate(this, s.createBinder());
		ActivityDelegate.setContextToDelegate(ctx -> d);
		delegate = completed(d);
		d.onActivityCreate(state);
		return d;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityResume);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		super.onDestroy();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityDestroy)
				.thenRun(() -> ActivityDelegate.setContextToDelegate(null));
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		View screen = findViewById(R.id.main_activity);
		return (screen instanceof ViewGroup group) ? group.findFocus() : null;
	}

	public boolean isCarActivity() {
		return true;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityFinish);
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;
		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
					stopInput();
					if (err instanceof OperationCanceledException) {
						textWatcher = w;
						editText.removeTextChangedListener(w);
						editText.addTextChangedListener(w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						a().startInput(editText);
					} else if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else {
						stopInput();
					}
				});
			} else {
				a().startInput(editText);
			}
		});
		return editText;
	}

	public void stopInput() {
		if (editText != null) {
			if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
			editText.setOnEditorActionListener(null);
		}

		a().stopInput();
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}

	@Override
	public boolean setTextInput(String text) {
		if ((editText == null) || !isInputActive()) return false;
		editText.setText(text);
		stopInput();
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyUp(keyCode, keyEvent);
		Boolean knob = onDashboardKnob(d, keyEvent);
		if (knob != null) return knob;

		if (d.getPrefs().useDpadCursor(d)) {
			switch (keyCode) {
				case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
						KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if (c != null) c.delayedHide();
					return true;
				}
				case KEYCODE_BACK -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.ignoreBack) {
						c.ignoreBack = false;
						return true;
					}
				}
				case KEYCODE_DPAD_CENTER -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.isFocused()) return c.click();
				}
			}
		}

		return d.onKeyUp(keyCode, keyEvent, super::onKeyDown);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyDown(keyCode, keyEvent);
		Boolean knob = onDashboardKnob(d, keyEvent);
		if (knob != null) return knob;
		if (!d.getPrefs().useDpadCursor(d)) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);

		float x = 0;
		float y = 0;
		View screen = null;
		Cursor cursor = null;

		switch (keyCode) {
			case KEYCODE_DPAD_UP -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_UP);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = -1;
			}
			case KEYCODE_DPAD_DOWN -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_DOWN);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = 1;
			}
			case KEYCODE_DPAD_LEFT -> x = -1;
			case KEYCODE_DPAD_RIGHT -> x = 1;
			case KEYCODE_DPAD_UP_LEFT -> {
				y = -1;
				x = -1;
			}
			case KEYCODE_DPAD_UP_RIGHT -> {
				y = -1;
				x = 1;
			}
			case KEYCODE_DPAD_DOWN_LEFT -> {
				y = 1;
				x = -1;
			}
			case KEYCODE_DPAD_DOWN_RIGHT -> {
				y = 1;
				x = 1;
			}
			case KEYCODE_BACK -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if ((cursor == null) || cursor.isFocused())
					return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
				cursor.ignoreBack = true;
			}
			case KEYCODE_DPAD_CENTER -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if (cursor == null) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
			}
			default -> {return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);}
		}

		if (screen == null) {
			screen = findViewById(R.id.main_activity);
			cursor = screen.findViewById(R.id.cursor);
		}

		int w = screen.getWidth();
		int h = screen.getHeight();

		if (cursor == null) {
			cursor = new Cursor(d, (int) (Math.min(w, h) * 0.05f));
			((ViewGroup) screen).addView(cursor);
			cursor.show(w / 2f, h / 2f);
		} else if (!cursor.isVisible()) {
			cursor.show(cursor.getX(), cursor.getY());
		} else {
			int cursorSize = (int) (Math.min(w, h) * 0.05f);
			cursor.move(w, h, x, y, cursorSize / 3f, keyCode);
		}

		return true;
	}

	/**
	 * @return true if HomeCar handled the event, false if Android Auto should
	 * move between screen portions, or null to keep the normal key path
	 */
	@Nullable
	private Boolean onDashboardKnob(MainActivityDelegate d, KeyEvent event) {
		if (d.getActiveMenu() != null) return null;
		int code = event.getKeyCode();
		boolean focus = (code == KeyEvent.KEYCODE_TAB) || (code == KeyEvent.KEYCODE_NAVIGATE_NEXT) ||
				(code == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS);
		boolean activate = (code == KEYCODE_DPAD_CENTER) || (code == KeyEvent.KEYCODE_ENTER) ||
				(code == KeyEvent.KEYCODE_NUMPAD_ENTER);
		boolean nudge = isKnobNudge(code);
		int rotate = knobRotateDirection(code, event);
		if (rotate != 0) {
			View screen = findViewById(R.id.main_activity);
			View knob = focusedKnob(screen);
			if ((knob != null) || hasKnobTarget(screen)) {
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					if (knob != null) moveKnobSibling(knob, rotate);
					else focusEdgeKnob(screen, rotate > 0);
				}
				return true;
			}
		}
		if (activate) {
			View screen = findViewById(R.id.main_activity);
			View focused = (screen instanceof ViewGroup group) ? group.findFocus() : null;
			if ((focused != null) && "homecar-knob".equals(focused.getTag())) {
				if (event.getAction() == KeyEvent.ACTION_UP) focused.performClick();
				return true;
			}
		}
		if (!focus && !activate && !nudge) {
			if ((event.getAction() == KeyEvent.ACTION_DOWN) && (event.getRepeatCount() == 0) &&
					(Key.get(code) == null) && (findDashboardWebView() != null)) {
				Log.i("Unmapped car key code=", code, " source=", event.getSource(), " scan=",
						event.getScanCode());
			}
			return null;
		}
		WebView wv = findDashboardWebView();
		if (wv == null) return null;
		hideKnobCursor();
		return wv.dispatchKeyEvent(event);
	}

	private static int knobRotateDirection(int code, KeyEvent event) {
		if (code == KeyEvent.KEYCODE_TAB) return event.isShiftPressed() ? -1 : 1;
		if ((code == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) || (code == KeyEvent.KEYCODE_NAVIGATE_OUT))
			return -1;
		if ((code == KeyEvent.KEYCODE_NAVIGATE_NEXT) || (code == KeyEvent.KEYCODE_NAVIGATE_IN))
			return 1;
		return 0;
	}

	@Nullable
	private static View focusedKnob(View screen) {
		if (!(screen instanceof ViewGroup group)) return null;
		View focused = group.findFocus();
		return ((focused != null) && "homecar-knob".equals(focused.getTag())) ? focused : null;
	}

	private static boolean hasKnobTarget(View screen) {
		return findKnobTarget(screen) != null;
	}

	@Nullable
	private static View findKnobTarget(View view) {
		if ("homecar-knob".equals(view.getTag())) return view;
		if (!(view instanceof ViewGroup group)) return null;
		for (int i = 0, n = group.getChildCount(); i < n; i++) {
			View found = findKnobTarget(group.getChildAt(i));
			if (found != null) return found;
		}
		return null;
	}

	private static void focusEdgeKnob(View screen, boolean first) {
		View any = findKnobTarget(screen);
		if (any == null) return;
		ViewParent parent = any.getParent();
		if (!(parent instanceof ViewGroup group) || (group.getChildCount() == 0)) return;
		View target = group.getChildAt(first ? 0 : group.getChildCount() - 1);
		target.requestFocusFromTouch();
	}

	private static boolean moveKnobSibling(View focused, int direction) {
		ViewParent parent = focused.getParent();
		if (!(parent instanceof ViewGroup group)) return false;
		int count = group.getChildCount();
		int index = group.indexOfChild(focused);
		if ((index < 0) || (count < 2)) return false;
		group.getChildAt(Math.floorMod(index + direction, count)).requestFocusFromTouch();
		return true;
	}

	private static boolean isKnobNudge(int keyCode) {
		return switch (keyCode) {
			case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT,
					KEYCODE_DPAD_UP_LEFT, KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT,
					KEYCODE_DPAD_DOWN_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
					KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
					KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> true;
			default -> false;
		};
	}

	private WebView findDashboardWebView() {
		View screen = findViewById(R.id.main_activity);
		return (screen == null) ? null : findVisibleWebView(screen);
	}

	private static WebView findVisibleWebView(View v) {
		if (v.getVisibility() != View.VISIBLE) return null;
		if (v instanceof WebView wv) {
			if ((wv.getWidth() > 0) && (wv.getHeight() > 0)) return wv;
		}
		if (v instanceof ViewGroup vg) {
			for (int i = vg.getChildCount() - 1; i >= 0; i--) {
				WebView found = findVisibleWebView(vg.getChildAt(i));
				if (found != null) return found;
			}
		}
		return null;
	}

	private void hideKnobCursor() {
		View cursor = findViewById(R.id.cursor);
		if (cursor != null) cursor.setVisibility(View.GONE);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress) :
				super.onKeyLongPress(keyCode, keyEvent);
	}

	private static final class Cursor extends AppCompatImageView
			implements View.OnClickListener, View.OnLongClickListener {
		private final MainActivityDelegate activity;
		private Cancellable move = Cancellable.CANCELED;
		private Cancellable hide = Cancellable.CANCELED;
		private Cancellable resetAccel = Cancellable.CANCELED;
		boolean ignoreBack;
		int accel = 1;

		Cursor(MainActivityDelegate d, int size) {
			super(d.getContext());
			activity = d;
			setId(R.id.cursor);
			setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
			setLayoutParams(new ConstraintLayout.LayoutParams(size, size));
			setImageDrawable(AppCompatResources.getDrawable(d.getContext(), R.drawable.cursor));
			setOnClickListener(this);
			setOnLongClickListener(this);

			Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
			StateListDrawable bg = new StateListDrawable();
			bg.addState(new int[]{android.R.attr.state_focused}, transparent);
			bg.addState(new int[]{}, transparent);
			setBackground(bg);
		}

		boolean isVisible() {
			return getVisibility() == VISIBLE;
		}

		@Override
		public void onClick(View v) {
			click();
		}

		boolean click() {
			delayedHide();
			float x = getX();
			float y = getY();
			OverlayMenu m = activity.getActiveMenu();
			if ((m instanceof ViewGroup v) && click(v, x - v.getX(), y - v.getY())) return true;
			click(screen(), x, y);
			return true;
		}

		private boolean click(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v instanceof WebView) {
							touch(v, x, y, true);
							return true;
						}
						if (v instanceof VideoView) {
							touch(v, x, y, false);
							return true;
						}
						if (v.isClickable()) {
							v.performClick();
							v.requestFocus();
							activity.post(this::requestFocus);
							return true;
						}

						if ((v instanceof ViewGroup vg) && click(vg, x, y)) return true;
						touch(v, x, y, false);
						return true;
					}
				}
			}
			return false;
		}

		private void touch(View v, float x, float y, boolean clearFocus) {
			long time = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
			if (clearFocus) {
				clearFocus();
				setVisibility(GONE);
			}
			v.dispatchTouchEvent(down);
			down.recycle();
			activity.postDelayed(() -> {
				MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
				v.dispatchTouchEvent(up);
				up.recycle();
				if (!clearFocus) activity.post(this::requestFocus);
			}, 100);
		}

		@Override
		public boolean onLongClick(View v) {
			delayedHide();
			longClick(screen(), getX(), getY());
			return true;
		}

		private void longClick(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v.isLongClickable()) {
							v.performLongClick();
							activity.post(this::requestFocus);
							return;
						}
						if (v instanceof ViewGroup vg) longClick(vg, x, y);
						return;
					}
				}
			}
		}

		void delayedHide() {
			move.cancel();
			move = Cancellable.CANCELED;
			hide.cancel();
			hide = activity.postDelayed(() -> {
				hide = Cancellable.CANCELED;
				clearFocus();
				setVisibility(GONE);
				MediaItemListView.focusActive(activity.getContext(), null);
			}, 5000);
		}

		void show(float cursorX, float cursorY) {
			setVisibility(VISIBLE);
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		void move(int w, int h, float dx, float dy, float step, int keyCode) {
			move.cancel();
			move = activity.postDelayed(() -> move(w, h, dx, dy, step, keyCode), 50);

			float cursorX = getX() + (step * dx * accel);
			float cursorY = getY() + (step * dy * accel);
			cursorX = Math.max(0, Math.min(w - step, cursorX));
			cursorY = Math.max(0, Math.min(h - step, cursorY));

			if (resetAccel.cancel()) accel += 1;
			resetAccel = activity.postDelayed(() -> {
				resetAccel = Cancellable.CANCELED;
				accel = 1;
			}, 200);

			if ((keyCode == KEYCODE_DPAD_UP) && (getY() == 0)) {
				scroll(true);
			} else if ((keyCode == KEYCODE_DPAD_DOWN) && (getY() >= screen().getHeight() - getHeight())) {
				scroll(false);
			}

			if (!focusFb(screen(), cursorX, cursorY)) requestFocus();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		private boolean focusFb(ViewGroup screen, float cursorX, float cursorY) {
			View fb = screen.findViewById(R.id.floating_button);
			float fbX = fb.getX();
			float fbY = fb.getY();
			if ((cursorX >= fbX) && (cursorX < fbX + fb.getWidth()) && (cursorY >= fbY) &&
					(cursorY < fbY + fb.getHeight())) {
				fb.requestFocus();
				return true;
			}
			return false;
		}

		private void scroll(boolean up) {
			ActivityFragment f = activity.getActiveFragment();
			if (f == null) return;
			View root = f.getView();
			if (root instanceof ViewGroup vg) scroll(up, vg);
		}

		private boolean scroll(boolean up, View v) {
			if (v instanceof RecyclerView rv) {
				LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
				if (lm == null) return false;
				int pos = lm.findFirstVisibleItemPosition();
				if (up) {
					if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
				} else {
					if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
				}
				return true;
			} else if (v instanceof WebView wv) {
				if (up) wv.pageUp(false);
				else wv.pageDown(false);
				return true;
			} else if (v instanceof ViewGroup vg) {
				for (int i = 0, n = vg.getChildCount(); i < n; i++) {
					if (scroll(up, vg.getChildAt(i))) return true;
				}
			}
			return false;
		}

		private ViewGroup screen() {
			return (ViewGroup) getParent();
		}
	}
}
