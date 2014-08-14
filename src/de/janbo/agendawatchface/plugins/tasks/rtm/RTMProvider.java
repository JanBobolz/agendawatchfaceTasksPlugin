package de.janbo.agendawatchface.plugins.tasks.rtm;

import java.util.Collections;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.janbo.agendawatchface.api.AgendaItem;
import de.janbo.agendawatchface.api.AgendaWatchfacePlugin;

public class RTMProvider extends AgendaWatchfacePlugin {
	@Override
	public String getPluginId() {
		return "de.janbo.agendawatchface.plugins.tasks.rtm";
	}

	@Override
	public String getPluginDisplayName() {
		return "Remember the Milk";
	}

	@Override
	public void onRefreshRequest(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean("pref_rtm_active", false)) {
			publishData(context, Collections.<AgendaItem> emptyList(), false);
			return;
		}
		context.startService(new Intent(context, RTMService.class));
	}

	@Override
	public void onShowSettingsRequest(Context context) {
		//Start our settings activity
		Intent intent = new Intent(context, SettingsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}
	
	private void setAlarm(Context context) {
		AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		long interval = Long.parseLong(prefs.getString("pref_rtm_sync_interval", "30"))*60*1000;
		PendingIntent intent = PendingIntent.getService(context, 0, new Intent(context, RTMService.class), 0);
		manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, System.currentTimeMillis()+interval, interval, intent);
	}
}
