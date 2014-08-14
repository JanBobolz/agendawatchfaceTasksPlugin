package de.janbo.agendawatchface.plugins.tasks.anydo;

import de.janbo.agendawatchface.plugins.tasks.R;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

public class SettingsFragment extends PreferenceFragment {
	SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			Intent intent = new Intent(getActivity().getApplicationContext(), AnyDoProvider.class);
			intent.setAction(TasksContract.INTENT_ACTION_TASKS_REFREHSED);
			getActivity().sendBroadcast(intent);
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferencesanydo);

		PreferenceScreen folderPickScreen = (PreferenceScreen) findPreference("pref_anydo_screen_folder_pick");
		for (String folder : AnyDoProvider.getAnyDoFolders(getActivity())) {
			CheckBoxPreference pref = new CheckBoxPreference(getActivity());
			pref.setDefaultValue(Boolean.TRUE);
			pref.setKey("pref_anydo_show_folder_" + folder);
			pref.setTitle(folder);

			folderPickScreen.addPreference(pref);
		}
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
}
