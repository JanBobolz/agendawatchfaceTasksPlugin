package de.janbo.agendawatchface.plugins.tasks.rtm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;
import de.janbo.agendawatchface.plugins.tasks.R;

public class SettingsFragment extends PreferenceFragment {
	SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			getActivity().startService(new Intent(getActivity().getApplicationContext(), RTMService.class));
		}
	};
	
	BroadcastReceiver authReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Toast.makeText(context, intent.getStringExtra(RTMService.INTENT_EXTRA_AUTH_RESULT), Toast.LENGTH_LONG).show();
		};
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferencesrtm);

		Preference authPref = findPreference("pref_rtm_authenticate");
		authPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(getActivity(), RTMService.class);
				intent.setAction(RTMService.INTENT_ACTION_START_AUTH);
				getActivity().startService(intent);
				
				setPrefToAuthenticated(preference);
				return true;
			}
		});
		if (prefs.contains("rtm_frob")) {
			setPrefToAuthenticated(authPref);
		}
		
		Preference checkAuthPref = findPreference("pref_rtm_check_auth");
		checkAuthPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(getActivity(), RTMService.class);
				intent.setAction(RTMService.INTENT_ACTION_CHECK_AUTH);
				getActivity().startService(intent);
				
				return true;
			}
		});
		
		//Register broadcast receiver
		IntentFilter filter = new IntentFilter();
		filter.addAction(RTMService.INTENT_ACTION_AUTH_RESULT);
		getActivity().registerReceiver(authReceiver, filter);
	}
	
	private void setPrefToAuthenticated(Preference authPref) {
		authPref.setTitle("Restart authentication with RTM");
		authPref.setSummary("Do this if something went wrong");
	}

	@Override
	public void onResume() {
		super.onResume();
		getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
	}

	@Override
	public void onPause() {
		super.onPause();
		getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		getActivity().unregisterReceiver(authReceiver);
	}
}
