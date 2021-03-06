/*
    This file is part of CheckMKAgent-Android.

    CheckMKAgent-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    CheckMKAgent-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CheckMKAgent-Android.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.aec.solutions.checkmkagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the service on System startup
 * @author lukasbi
 *
 */
public class StartUpBroadCastReceiver extends BroadcastReceiver
{
	protected static final String TAG = StartUpBroadCastReceiver.class.getName();

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
