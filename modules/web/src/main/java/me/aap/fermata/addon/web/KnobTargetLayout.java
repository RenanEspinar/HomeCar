package me.aap.fermata.addon.web;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Rotation walks these targets with forward focus. A tilt in any direction
 * finds nothing here, so Android Auto can leave the dashboard.
 */
public class KnobTargetLayout extends FrameLayout {

	public KnobTargetLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public View focusSearch(View focused, int direction) {
		if ((direction == FOCUS_FORWARD) || (direction == FOCUS_BACKWARD)) {
			return super.focusSearch(focused, direction);
		}
		return null;
	}
}
