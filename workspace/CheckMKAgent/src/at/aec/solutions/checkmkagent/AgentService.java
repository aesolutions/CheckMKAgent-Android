package at.aec.solutions.checkmkagent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

public class AgentService extends Service {
	protected static final String TAG = AgentService.class.getName();
	
	private static String NEWLINE = System.getProperty("line.separator");
	
	private Thread socketServerThread;

	private ServerSocket m_serverSocket = null;

	private boolean m_isListening = false;

	private int mId;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.v(TAG, "onCreate");

		socketServerThread = new Thread(new SocketServerThread());
		socketServerThread.start();

		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(R.drawable.ic_launcher)
		        .setContentTitle("CheckMK-Agent started.")
		        .setContentText("Tap to configure");
		
		Intent resultIntent = new Intent(this, ConfigureActivity.class);
		
//		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager =
		    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		Notification notify = mBuilder.build();
		notify.flags |= Notification.FLAG_NO_CLEAR;
		
		mNotificationManager.notify(mId, notify);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.v(TAG, "onStartCommand");

		return START_STICKY;

	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.v(TAG, "onDestroy");
		
		m_isListening = false;

		if (m_serverSocket != null) {
			try {
				m_serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private class SocketServerThread extends Thread
	{
		private static final int SERVERPORT = 6556;

		@Override
		public void run()
		{
			try
			{
				m_serverSocket = new ServerSocket(SERVERPORT);

				while (true)
				{
					Socket socket = m_serverSocket.accept();

					SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket);
					socketServerReplyThread.run();

				}
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private class SocketServerReplyThread extends Thread
	{

		private Socket hostThreadSocket;

		SocketServerReplyThread(Socket socket)
		{
			hostThreadSocket = socket;
		}

		@Override
		public void run()
		{
			try
			{
				PrintWriter out = new PrintWriter(hostThreadSocket.getOutputStream(),true);
				
				writeHeader(out);
				writeSystemInformation(out);
				writeProcessList(out);
				writeMemoryInfo(out);
				writeCPULoad(out);
				writeUptime(out);
				writeNetIf(out);
				writeTCPConnStats(out);
				writeDiskStat(out);
				writeKernel(out);
				writeDMIDecode(out);
				writeCoreTemp(out);
				
				out.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void writeHeader(PrintWriter _out)
	{
//		Process ps = Runtime.getRuntime().exec("ps");
//		
//		BufferedReader is = new BufferedReader(new InputStreamReader(ps.getInputStream()));
//		String line;
//		while ((line = is.readLine()) != null)
//		{
//			System.out.println(line);
//		}
		
		PackageManager m = getPackageManager();
		String s = getPackageName();
		try {
		    PackageInfo p = m.getPackageInfo(s, 0);
		    s = p.applicationInfo.dataDir;
		} catch (NameNotFoundException e) {
		    Log.w("yourtag", "Error Package name not found ", e);
		}
		
		_out.write("<<<check_mk>>>"+NEWLINE);
		_out.write("Version: 1.2.0p2"+NEWLINE);
		_out.write("AgentOS: android"+NEWLINE);
		_out.write("PluginsDirectory: "+s+"/plugins"+NEWLINE);
		_out.write("LocalDirectory: "+s+"/local"+NEWLINE);
		_out.write("AgentDirectory: "+s+NEWLINE);
		_out.write("OnlyFrom: "+NEWLINE);
	}

	private void writeSystemInformation(PrintWriter _out)
	{
		//TODO: Format the output correctly
		
		Process df_proc;
		try {
			df_proc = Runtime.getRuntime().exec("df");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(df_proc.getInputStream()));
			String line;
			is.readLine(); // skip the first line
			_out.write("<<<df>>>"+NEWLINE);
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Process mount_proc;
		try {
			mount_proc = Runtime.getRuntime().exec("mount");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(mount_proc.getInputStream()));
			String line;
			
			_out.write("<<<nfsmounts>>>"+NEWLINE);
			_out.write("<<<mounts>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void writeProcessList(PrintWriter _out)
	{
		Process ps_proc;
		try {
			ps_proc = Runtime.getRuntime().exec("ps");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(ps_proc.getInputStream()));
			String line;
			
			_out.write("<<<ps>>>"+NEWLINE);
			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void writeMemoryInfo(PrintWriter _out)
	{
		Process mem_proc;
		try {
			mem_proc = Runtime.getRuntime().exec("cat /proc/meminfo");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(mem_proc.getInputStream()));
			String line;
			
			_out.write("<<<mem>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void writeCPULoad(PrintWriter _out)
	{
		Process loadavg_proc;
		try {
			loadavg_proc = Runtime.getRuntime().exec("cat /proc/loadavg");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(loadavg_proc.getInputStream()));
			String line;
			
			_out.write("<<<cpu>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void writeUptime(PrintWriter _out)
	{
		Process uptime_proc;
		try {
			uptime_proc = Runtime.getRuntime().exec("cat /proc/uptime");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(uptime_proc.getInputStream()));
			String line;
			
			_out.write("<<<uptime>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void writeNetIf(PrintWriter _out)
	{
		Process net_proc;
		try {
			net_proc = Runtime.getRuntime().exec("cat .");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(net_proc.getInputStream()));
			String line;
			
			_out.write("<<<netif>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Process newnet_proc;
		try {
			newnet_proc = Runtime.getRuntime().exec("cat /proc/net/dev");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(newnet_proc.getInputStream()));
			String line;
			
			_out.write("<<<lnx_if:sep(58)>>>"+NEWLINE);
			is.readLine(); // skip the first line
			is.readLine(); // skip the second line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	private void writeTCPConnStats(PrintWriter _out)
	{
		HashMap<String, Integer> netstatVal = new HashMap<String, Integer>();
		Process tcpconn_proc;
		try {
			tcpconn_proc = Runtime.getRuntime().exec("netstat");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(tcpconn_proc.getInputStream()));
			String line;
			_out.write("<<<tcp_conn_stats>>>"+NEWLINE);
			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				String[] linevals = line.split(" ");
//				Log.v(TAG, linevals[linevals.length-1]);
				if(netstatVal.containsKey(linevals[linevals.length-1]))
				{
					netstatVal.put(linevals[linevals.length-1], netstatVal.get(linevals[linevals.length-1])+1);
				}
				else
				{
					netstatVal.put(linevals[linevals.length-1],1);
				}
			}
			
			Iterator it = netstatVal.entrySet().iterator();
		    while (it.hasNext()) {
		        Map.Entry pairs = (Map.Entry)it.next();
		        _out.write(pairs.getKey()+" "+pairs.getValue()+NEWLINE);
//		        System.out.println(pairs.getKey() + " = " + pairs.getValue());
//		        it.remove(); // avoids a ConcurrentModificationException
		    }
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	private void writeSoftRaid(PrintWriter _out)
	{
		_out.write("<<<md>>>"+NEWLINE);
	}
	
	private void writeDiskStat(PrintWriter _out)
	{		
		Process diskstats_proc;
		try {
			diskstats_proc = Runtime.getRuntime().exec("cat /proc/diskstats");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(diskstats_proc.getInputStream()));
			String line;
			
			long unixTime = System.currentTimeMillis() / 1000L;
			_out.write("<<<diskstat>>>"+NEWLINE);
			_out.write(unixTime+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private void writeKernel(PrintWriter _out)
	{
		Process kernel_proc;
		try {
			kernel_proc = Runtime.getRuntime().exec("cat /proc/vmstat /proc/stat");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(kernel_proc.getInputStream()));
			String line;
			
			_out.write("<<<kernel>>>"+NEWLINE);
			long unixTime = System.currentTimeMillis() / 1000L;
			_out.write(unixTime+NEWLINE);
			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}	
	}
	
	private void writeDMIDecode(PrintWriter _out)
	{
		_out.write("<<<dmi_sysinfo>>>"+NEWLINE);
		_out.write("System Information"+NEWLINE);
		_out.write("    Manufacturer: "+Build.MANUFACTURER+" ("+Build.BRAND+")"+NEWLINE);
		_out.write("    Product Name: "+Build.MODEL+NEWLINE);
		_out.write("    Version: "+Build.VERSION.RELEASE+NEWLINE);
		_out.write("    Serial Number: "+Build.DISPLAY+NEWLINE);
		TelephonyManager tManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		String uuid = tManager.getDeviceId();
		_out.write("    UUID: "+uuid+NEWLINE);
		_out.write("    Wake-up Type: Power Switch"+NEWLINE);
		_out.write("    SKU Number: Not Specified"+NEWLINE);
		_out.write("    Family: Not Specified"+NEWLINE);
	}
	
	private void writeCoreTemp(PrintWriter _out)
	{
		Process temp_proc;
		try {
			temp_proc = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(temp_proc.getInputStream()));
			String line;
			
			_out.write("<<<cputemp>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}	
	}
}
