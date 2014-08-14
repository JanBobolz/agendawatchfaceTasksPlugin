package de.janbo.agendawatchface.plugins.tasks.anydo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;
import de.janbo.agendawatchface.api.AgendaItem;
import de.janbo.agendawatchface.api.AgendaWatchfacePlugin;
import de.janbo.agendawatchface.api.LineOverflowBehavior;
import de.janbo.agendawatchface.api.TimeDisplayType;

public class AnyDoProvider extends AgendaWatchfacePlugin {

	@Override
	public void onReceive(Context context, Intent intent) {
		if (TasksContract.INTENT_ACTION_TASKS_REFREHSED.equals(intent.getAction())) //refresh if any.do broadcasts a (potential) change
			onRefreshRequest(context);
		else
			super.onReceive(context, intent); //AgendaWatchface plugin communication
	}

	@Override
	public String getPluginId() {
		return "de.janbo.agendawatchface.plugins.tasks.anydo";
	}

	@Override
	public String getPluginDisplayName() {
		return "Any.do";
	}

	@Override
	public void onRefreshRequest(Context context) {
		ArrayList<AgendaItem> items = new ArrayList<AgendaItem>();
		
		//Read preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (!prefs.getBoolean("pref_anydo_active", true)) {
			publishData(context, items, false);
			return;
		}
		boolean pref_show_only_today = prefs.getBoolean("pref_anydo_show_only_today", false);
		boolean pref_show_only_important = prefs.getBoolean("pref_anydo_show_only_important", false);
		boolean pref_bold_font = prefs.getBoolean("pref_anydo_bold_text", false);
		String pref_prefix_normal = prefs.getString("pref_anydo_task_prefix", "! ");
		String pref_prefix_important = prefs.getString("pref_anydo_task_prefix_important", "!! ");
		
		
		//Get data from any.do
		String[] projTasks = null;
		Cursor tasks = context.getContentResolver().query(TasksContract.TASKS_URI, projTasks, TasksContract.TasksColumns.STATUS + "=?", new String[] { String.valueOf(TasksContract.STATUS_UNCHECKED) },
				null, null);
		if (tasks == null) {
			Log.e("AgendaWatchface AnyDoProvider", "Query returned null");
			publishData(context, items, false);
			return;
		}
			
		
		while (tasks.moveToNext()) {
			AgendaItem item = new AgendaItem(getPluginId());
			
			//Filter by folder
			String folder = tasks.getString(tasks.getColumnIndex(TasksContract.TasksColumns.CATEGORY_NAME));
			if (folder == null)
				folder = "noFolder";
			if (!prefs.getBoolean("pref_anydo_show_folder_"+folder, true))
				continue; //skip those we don't want to show
			
			//Figure out due date
			String due = tasks.getString(tasks.getColumnIndex(TasksContract.TasksColumns.DUE_DATE));
			if (due != null) {
				if (!due.equals("0"))
					try {
						item.startTime = new Date(Long.parseLong(due)); //set task to appear at the time/day that is was set to appear
					} catch (NumberFormatException e) { Log.e("AnyDoProvider", "Invalid number format: \""+due+"\" for event "+tasks.getString(tasks.getColumnIndex(TasksContract.TasksColumns.TITLE))); }
				else
					item.startTime = null; //by default, don't give it a special time (item will appear on top of the watchface)
			} else
				continue; //don't show "someday" tasks
			
			if (pref_show_only_today && item.startTime != null) { //filter
				Calendar cal = Calendar.getInstance();
				Calendar now = Calendar.getInstance();
				cal.setTime(item.startTime);
				if (cal.compareTo(now) >= 0 && (cal.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) || cal.get(Calendar.YEAR) != now.get(Calendar.YEAR)))
					continue; //skip tasks that are not today
			}
			
			//Figure out priority, display certain number of exclamation marks 
			int priority = 0;
			try {
				priority = Integer.parseInt(tasks.getString(tasks.getColumnIndex(TasksContract.TasksColumns.PRIORITY)))-1;
			} catch (RuntimeException e) {}
			if (pref_show_only_important && priority < 2)
				continue; //skip the unimportant ones if set to
			
			item.priority = priority+1; //makes sure that more important items are displayed first
			
			//Set what we want to display on the watch
			item.line1.text = (priority < 2 ? pref_prefix_normal : pref_prefix_important)
					+tasks.getString(tasks.getColumnIndex(TasksContract.TasksColumns.TITLE));
			item.line1.timeDisplay = TimeDisplayType.NONE; //don't show time
			item.line1.textBold = pref_bold_font;
			item.line1.overflow = prefs.getBoolean("pref_anydo_long_titles", true) ? LineOverflowBehavior.OVERFLOW_IF_NECESSARY : LineOverflowBehavior.NONE;
			item.line2 = null;
			
			items.add(item);
		}
		tasks.close();
		
		//Send data to the AgendaWatchface service
		publishData(context, items, false);
	}

	@Override
	public void onShowSettingsRequest(Context context) {
		//Start our settings activity
		Intent intent = new Intent(context, SettingsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}
	
	public static List<String> getAnyDoFolders(Context context) {
		ArrayList<String> result = new ArrayList<String>();
		Cursor folders = context.getContentResolver().query(TasksContract.FOLDERS_URI, null, null, null, null, null);
		while (folders.moveToNext())
			result.add(folders.getString(folders.getColumnIndex(TasksContract.FolderColumns.NAME)));
		folders.close();
		return result;
	}
}
