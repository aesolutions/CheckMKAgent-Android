package at.aec.solutions.checkmkagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartUpBroadCastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction()))
		{
			Intent pushIntent = new Intent(context, AgentService.class);
			context.startService(pushIntent);
		}
	}
}
