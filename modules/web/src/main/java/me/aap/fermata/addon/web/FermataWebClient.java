package me.aap.fermata.addon.web;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.security.KeyChain;
import android.webkit.ClientCertRequest;
import android.webkit.WebView;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import android.net.Uri;
import android.webkit.WebResourceRequest;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;

	private static final String TLS_PREFS = "homecar_client_cert";

	@Override
	public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
		if (!(view instanceof FermataWebView browser)) {
			request.ignore();
			return;
		}
		WebBrowserAddon addon = browser.getAddon();
		if (addon == null) {
			request.ignore();
			return;
		}
		Uri configured = Uri.parse(addon.getHomeUrl());
		String host = request.getHost();
		int port = request.getPort();
		if (!"https".equalsIgnoreCase(configured.getScheme()) || host == null ||
				configured.getHost() == null ||
				!host.equalsIgnoreCase(configured.getHost()) ||
				port != ((configured.getPort() == -1) ? 443 : configured.getPort())) {
			request.ignore();
			return;
		}

		Context ctx = view.getContext();
		SharedPreferences prefs = ctx.getSharedPreferences(TLS_PREFS, Context.MODE_PRIVATE);
		String key = host.toLowerCase(java.util.Locale.ROOT) + ":" + port;
		String savedAlias = prefs.getString(key, null);
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(ctx).peek();
		if (a != null && a.getAppActivity() instanceof Activity phoneActivity &&
				!a.isCarActivityNotMirror()) {
			// KeyChain performs explicit user selection/authorization on the phone.
			KeyChain.choosePrivateKeyAlias(phoneActivity, alias -> {
				if (alias == null) {
					request.ignore();
					return;
				}
				prefs.edit().putString(key, alias).apply();
				provideClientCertificate(ctx, request, alias);
			}, request.getKeyTypes(), request.getPrincipals(),
					Uri.parse("https://" + host + ":" + port), savedAlias);
		} else if (savedAlias != null) {
			// The projected CarActivity cannot show the phone KeyChain picker.
			provideClientCertificate(ctx, request, savedAlias);
		} else {
			Log.w("No mTLS client certificate authorized. Open HomeCar on the phone first.");
			request.ignore();
		}
	}

	private static void provideClientCertificate(Context ctx, ClientCertRequest request,
			String alias) {
		new Thread(() -> {
			try {
				PrivateKey key = KeyChain.getPrivateKey(ctx, alias);
				X509Certificate[] chain = KeyChain.getCertificateChain(ctx, alias);
				if (key == null || chain == null || chain.length == 0) {
					Log.w("Configured mTLS client certificate is unavailable");
					request.ignore();
				} else {
					request.proceed(key, chain);
				}
			} catch (Exception ex) {
				Log.e(ex, "Failed to access authorized mTLS certificate");
				request.ignore();
			}
		}, "HomeCar-KeyChain").start();
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.getActivityDelegate(view.getContext())
					.onSuccess(a -> a.setContentLoading(new Promise<>()));
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		FutureSupplier<MainActivityDelegate> f =
				MainActivityDelegate.getActivityDelegate(v.getContext());
		f.onSuccess(a -> a.setContentLoading(Completed.completedVoid()));

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		v.hideKeyboard();
		v.pageLoaded(url);
		f.onSuccess(a -> a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED));
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view,
											@NonNull WebResourceRequest request) {
		Uri target = request.getUrl();
		String scheme = target.getScheme();

		if ((scheme == null)
				|| (!"http".equalsIgnoreCase(scheme)
				&& !"https".equalsIgnoreCase(scheme))) {
			Log.w("Blocked WebView navigation: " + target);
			return true;
		}

		if (isYoutubeUri(target)) {
			try {
				MainActivityDelegate a =
						MainActivityDelegate.getActivityDelegate(view.getContext()).peek();
				if (a == null) return false;

				if (!(a.showFragment(me.aap.fermata.R.id.youtube_fragment)
						instanceof YoutubeFragment f)) {
					return false;
				}

				f.loadUrl(target.toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		return ((host != null)
				&& ((host.endsWith("youtube.com") && !host.endsWith("tv.youtube.com"))
				|| host.equals("youtu.be")));
	}

	@Override
	public void onReceivedError(@NonNull WebView view,
								@NonNull WebResourceRequest request,
								@NonNull WebResourceErrorCompat error) {
		if (WebViewFeature.isFeatureSupported(
				WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			Log.e("Web error received: " + error.getDescription());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}
