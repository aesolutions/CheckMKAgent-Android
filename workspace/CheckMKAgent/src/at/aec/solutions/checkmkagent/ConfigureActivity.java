package at.aec.solutions.checkmkagent;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ConfigureActivity extends Activity {

	private TextView lblStatus;
	private Button cmdStartService;
	private Button cmdStopService;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_configure);
		
//		Toast.makeText(getApplicationContext(), "Service running: "+isMyServiceRunning(AgentService.class), Toast.LENGTH_LONG).show();
		
		lblStatus = (TextView) findViewById(R.id.lblStatus);
		cmdStartService = (Button) findViewById(R.id.btnStartService);
		cmdStopService = (Button) findViewById(R.id.btnStopService);
		
		setStatusLabel();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.configure, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
	    {
	        if (serviceClass.getName().equals(service.service.getClassName()))
	        {
	            return true;
	        }
	    }
	    return false;
	}
	
	private void setStatusLabel()
	{
		if(isMyServiceRunning(AgentService.class))
		{
			lblStatus.setText(getResources().getString(R.string.lblstatus)+"running");
			lblStatus.setBackgroundColor(Color.rgb(58, 95, 11));
			cmdStartService.setEnabled(false);
			cmdStopService.setEnabled(true);
		}
		else
		{
			lblStatus.setText(getResources().getString(R.string.lblstatus)+"not running");
			lblStatus.setBackgroundColor(Color.rgb(170, 1, 20));
			cmdStartService.setEnabled(true);
			cmdStopService.setEnabled(false);
		}
	}
}
