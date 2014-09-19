package de.janbo.agendawatchface.plugins.tasks.rtm;

import it.bova.rtmapi.Permission;
import it.bova.rtmapi.RtmApi;
import it.bova.rtmapi.RtmApiAuthenticator;
import it.bova.rtmapi.RtmApiException;
import it.bova.rtmapi.ServerException;
import it.bova.rtmapi.Task;
import it.bova.rtmapi.Token;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import de.janbo.agendawatchface.api.AgendaItem;
import de.janbo.agendawatchface.api.LineOverflowBehavior;
import de.janbo.agendawatchface.api.TimeDisplayType;

public class RTMService extends IntentService {
	public static final String INTENT_ACTION_START_AUTH = "de.janbo.agendawatchface.plugins.tasks.intent.action.startauth";
	public static final String INTENT_ACTION_CHECK_AUTH = "de.janbo.agendawatchface.plugins.tasks.intent.action.checkauth";
	
	//Broadcast actions
	public static final String INTENT_ACTION_AUTH_RESULT = "de.janbo.agendawatchface.plugins.tasks.intent.action.authresult";
	public static final String INTENT_EXTRA_AUTH_RESULT = "de.janbo.agendawatchface.plugins.tasks.intent.extra.authresult"; //String

	public RTMService() {
		super("Agenda Watchface RTM Service");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (INTENT_ACTION_START_AUTH.equals(intent.getAction()))
			startAuthentication();
		else if (INTENT_ACTION_CHECK_AUTH.equals(intent.getAction()))
			checkAuthentication();
		else
			updateData();
	}
	
	/**
	 * Updates the AgendaWatchface service with new data
	 */
	protected void updateData() {
		ArrayList<AgendaItem> items = new ArrayList<AgendaItem>();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		if (!prefs.getBoolean("pref_rtm_active", false))
			return;
		
		LineOverflowBehavior overflow = prefs.getBoolean("pref_rtm_long_titles", true) ? LineOverflowBehavior.OVERFLOW_IF_NECESSARY : LineOverflowBehavior.NONE;
		boolean bold = prefs.getBoolean("pref_rtm_bold_text", false);
		
		String token = getToken();
		String pluginId = new RTMProvider().getPluginId();
		String filterExpr = prefs.getString("pref_rtm_filter", "");
		String[] prefixes = new String[] {
				prefs.getString("pref_rtm_task_prefix", "! "),
				prefs.getString("pref_rtm_task_prefix_3", "! "),
				prefs.getString("pref_rtm_task_prefix_2", "!! "),
				prefs.getString("pref_rtm_task_prefix_1", "!!! "),
		};
		
		if (token == null) {
			new RTMProvider().publishData(getApplicationContext(), items, false);
			return;
		}
		
		RtmApi api = new RtmApi(APIConstants.API_KEY, APIConstants.SHARED_SECRET, token);
		List<Task> tasks = new ArrayList<Task>();
		
		try {
			tasks = api.tasksGetByFilter("("+filterExpr+") AND status:incomplete");
			
			for (Task task : tasks) {
				if (task.getCompleted() != null)
					continue;
				if (items.size() > 20)
					break;
				AgendaItem item = new AgendaItem(pluginId);
				
				//Figure out text
				String prefix = "";
				switch (task.getPriority()) {
				case HIGH:
					prefix = prefixes[3];
					item.priority = 3;
					break;
				case LOW:
					prefix = prefixes[1];
					item.priority = 1;
					break;
				case MEDIUM:
					prefix = prefixes[2];
					item.priority = 2;
					break;
				case NONE:
					prefix = prefixes[0];
					item.priority = 0;
					break;
				default:
					break;
				}
				item.line1.text = prefix+task.getName();
				item.line1.overflow = overflow;
				item.line1.textBold = bold;
				
				//Time
				if (task.getHasDueTime())
					item.line1.timeDisplay = TimeDisplayType.START_TIME;
				item.startTime = task.getDue();
				
				items.add(item);
			}
		} catch (ServerException e) {
			Log.e("RTMService", "Error", e);
		} catch (RtmApiException e) {
			Log.e("RTMService", "Error", e);
		} catch (IOException e) {
			Log.e("RTMService", "Error", e);
		}
		
		new RTMProvider().publishData(getApplicationContext(), items, false);
	}

	/**
	 * Broadcasts (via Android) a String describing the current authentication status
	 */
	protected void checkAuthentication() {
		String authenticated = "Not authenticated";
		String token = getToken();
		
		if (token != null) {
			RtmApiAuthenticator authenticator = new RtmApiAuthenticator(APIConstants.API_KEY, APIConstants.SHARED_SECRET);
			try {
				Token tokenObj = authenticator.authCheckToken(token);
				if (tokenObj != null && tokenObj.getPermission() != Permission.NONE)
					authenticated = "Properly authenticated!";
			} catch (ServerException e) {
				Log.e("RTMService", "Error", e);
			} catch (RtmApiException e) {
				Log.e("RTMService", "Error", e);
			} catch (IOException e) {
				Log.e("RTMService", "Error", e);
			}
		} 
		
		Intent intent = new Intent(INTENT_ACTION_AUTH_RESULT);
		intent.putExtra(INTENT_EXTRA_AUTH_RESULT, authenticated);
		sendBroadcast(intent);
	}

	/**
	 * Starts first half of authentication process: creates a frob and starts an activity for the user to authenticate the request
	 */
	protected void startAuthentication() {
		RtmApiAuthenticator authenticator = new RtmApiAuthenticator(APIConstants.API_KEY, APIConstants.SHARED_SECRET);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		try {
			String frob = authenticator.authGetFrob();
			prefs.edit().putString("rtm_frob", frob).remove("rtm_token").commit();
			String validationUrl = authenticator.authGetDesktopUrl(Permission.READ,frob);
			
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(validationUrl));
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} catch (IOException e) {
			Log.e("RTMService", "Error", e);
		} catch (ServerException e) {
			Log.e("RTMService", "Error", e);
		} catch (RtmApiException e) {
			Log.e("RTMService", "Error", e);
		}
	}
	
	/**
	 * Tries to get a token either from storage or by getting it from a frob
	 */
	protected String getToken() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		
		if (prefs.contains("rtm_token"))
			return prefs.getString("rtm_token", null);
		
		if (prefs.contains("rtm_frob")) {
			RtmApiAuthenticator authenticator = new RtmApiAuthenticator(APIConstants.API_KEY, APIConstants.SHARED_SECRET);
			try {
				Token authToken = authenticator.authGetToken(prefs.getString("rtm_frob", null));
				prefs.edit().putString("rtm_token", authToken.getToken()).commit();
				return authToken.getToken();
			} catch (ServerException e) {
				Log.e("RTMService", "Error", e);
			} catch (RtmApiException e) {
				Log.e("RTMService", "Error", e);
			} catch (IOException e) {
				Log.e("RTMService", "Error", e);
			}
		}
		
		return null;
	}
}
