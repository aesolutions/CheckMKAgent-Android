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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * The Service itself. It starts a multithreaded TCP socket Server that listen on Port 6556
 * After a client connects, the server starts a new thread, which prints the whole CheckMK-Stuff
 * out and closes the connection afterwards.
 * The Server uses for some information Busybox, which will be copied from assets to the app
 * folder on first run and makes it executable.
 * @author lukasbi
 *
 */
public class AgentService extends Service {
	protected static final String TAG = AgentService.class.getName();
	
	private static String NEWLINE = System.getProperty("line.separator");
	
	private Thread socketServerThread;

	private ServerSocket m_serverSocket = null;
	private static final int SERVERPORT = 6556;

	private boolean m_isListening = false;

	private int mId;

	private WakeLock m_wakeLock;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * Create the Service. Opens a socket and accept incoming connections.
	 * On first run, copy the busybox binary depending on architecture to
	 * app directory and makes it executable.
	 */
	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.v(TAG, "onCreate");
		
		PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
		m_wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
		        "MyWakelockTag");
		m_wakeLock.acquire();
		
		//Copy busybox binary to app directory
		if (!PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("bbinstalled", false))
		{
			PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext())
					.edit().putBoolean("bbinstalled", true).commit();

			// There is also String[] Build.SUPPORTED_ABIS from API 21 on and
			// before String Build.CPU_ABI String Build.CPU_ABI2, maybe i should investigate there
			String arch = System.getProperty("os.arch");
			Log.v(TAG,arch);
			if(arch.equals("armv7l"))
			{
				copyAsset(getAssets(), "bbb/busybox", getApplicationInfo().dataDir+"/busybox");
			}
			if(arch.equals("i686"))
			{
				copyAsset(getAssets(), "bbb/busybox-i686", getApplicationInfo().dataDir+"/busybox");
			}
			
			
//			copyAsset(getAssets(), "bbb/busybox-x86_64", getApplicationInfo().dataDir+"/busybox-x86_64");
			changeBusyboxPermission();
		}
		

		socketServerThread = new Thread(new SocketServerThread());
		socketServerThread.setName("CheckMK-Agent ServerThread");
		socketServerThread.start();

		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(R.drawable.ic_launcher)
		        .setContentTitle("CheckMK Agent started.")
		        .setContentText("Listening on Port "+SERVERPORT+". Tap to configure.");
		
		Intent resultIntent = new Intent(this, ConfigureActivity.class);
		
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(ConfigureActivity.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent =
		        stackBuilder.getPendingIntent(
		            0,
		            PendingIntent.FLAG_UPDATE_CURRENT
		        );
		
		mBuilder.setContentIntent(resultPendingIntent);
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
	public void onDestroy()
	{
		super.onDestroy();
		Log.v(TAG, "onDestroy");
		
		m_isListening = false;
		
		if(m_wakeLock != null)
		{
			m_wakeLock.release();
		}

		if (m_serverSocket != null) {
			try
			{
				m_serverSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG,"Server Socket Error: "+e.getMessage());
			}
		}
		
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mNotificationManager.cancel(mId);
	}

	private class SocketServerThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				m_serverSocket = new ServerSocket(SERVERPORT);
				m_isListening=true;
				
				int i =0;

				while (m_isListening)
				{
					Socket socket = m_serverSocket.accept();

					SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(socket);
					socketServerReplyThread.setName("AgentThread "+(i++));
					socketServerReplyThread.run();

				}
			}
			catch (IOException e)
			{
				Log.e(TAG,e.getMessage());
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
				writeProcRank(out);
				writeAppMemUsage(out, "at.aec.solutions.checkmkagent");
				writeAppMemUsage(out, "at.aec.solutions.adcolumnng");
				
				out.close();
				
				hostThreadSocket.close();
			}
			catch (IOException e)
			{
				Log.e(TAG,e.getMessage());
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
		String separator = "          ";
		
        HashMap<String, String> fstypes = new HashMap<String, String>();
        int hugestdev = 0;
        int hugestfs = 0;
		try
		{
			BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"));
	        String mountsline;
	        while((mountsline = br.readLine()) != null)
	        {
	        	String[] linevals = mountsline.split(" +");
	        	if(!fstypes.containsKey(linevals[0]))
	        	{
	        		if(linevals[0].length() > hugestdev)
	        		{
	        			hugestdev = linevals[0].length();
	        		}
	        		if(linevals[2].length() > hugestfs)
	        		{
	        			hugestfs = linevals[2].length();
	        		}
	        		fstypes.put(linevals[0], linevals[2]);
	        	}
	        }
	        br.close();
		}
		catch(FileNotFoundException _fnfex)
		{
			Log.e(TAG,"can't find /proc/mounts");
		}
		catch (IOException e)
		{
			Log.e(TAG,"can't read /proc/mounts");
		}
        
		Process df_proc;
		try
		{
			df_proc = Runtime.getRuntime().exec(getApplicationInfo().dataDir+"/busybox df -P");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(df_proc.getInputStream()));
			String line;
			is.readLine(); // skip the first line
			_out.write("<<<df>>>"+NEWLINE);
			while ((line = is.readLine()) != null)
			{
				String[] linevals = line.split(" +");
				String outline = linevals[0] + separator
						+ fstypes.get(linevals[0]) + separator + linevals[1]
						+ separator + linevals[2] + separator + linevals[3]
						+ separator + linevals[4] + separator + linevals[5];
				_out.write(outline + NEWLINE);
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}

		Process mount_proc;
		try {
			mount_proc = Runtime.getRuntime().exec("cat /proc/mounts");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(mount_proc.getInputStream()));
			String line;
			
			_out.write("<<<nfsmounts>>>"+NEWLINE);
			_out.write("<<<mounts>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
//				if(line.substring(0, 4).equals())
				if(line.contains("/dev"))
				{
					_out.write(line+NEWLINE);
				}
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}
	}
	
	private void writeProcessList(PrintWriter _out)
	{
		Process ps_proc;
		try {
			ps_proc = Runtime.getRuntime().exec("top -n 1 -d 0"); //Old was: ps, but ps on android doesn't show CPU Utilization
			
			BufferedReader is = new BufferedReader(new InputStreamReader(ps_proc.getInputStream()));
			String line;
			

			_out.write("<<<ps>>>"+NEWLINE);
			is.readLine(); // skip the first line
			is.readLine(); // skip the second line
			is.readLine(); // skip the third line
			is.readLine(); // skip the fourth line
			is.readLine(); // skip the fifth line
			is.readLine(); // skip the sixth line
			is.readLine(); // skip the seventh line
			while ((line = is.readLine()) != null)
			{
				//ps ax -o user,vsz,rss,pcpu,command
				//(root,20008,976,0.0) /sbin/getty -8 38400 tty1
				String[] linevals = line.trim().split(" +");
				
				double cpu = 0;
				try
				{
					cpu = Double.parseDouble(linevals[2].substring(0, linevals[2].length()-1));
					cpu = cpu /100;
				}
				catch(NumberFormatException _nfex)
				{
					Log.e(TAG, linevals[2]);
				}
				
				if(linevals.length == 9)
				{
					String outline = "("+linevals[7]+","+parseHRNumber(linevals[5])+","+parseHRNumber(linevals[6])+","+cpu+") "+linevals[8];
					_out.write(outline+NEWLINE);
				}
				else if(linevals.length == 10)
				{
					String outline = "("+linevals[8]+","+parseHRNumber(linevals[5])+","+parseHRNumber(linevals[6])+","+cpu+") "+linevals[9];
					_out.write(outline+NEWLINE);
				}
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}		
	}
	
	private void writeCPULoad(PrintWriter _out)
	{
		Process loadavg_proc;
		try
		{
			loadavg_proc = Runtime.getRuntime().exec("cat /proc/loadavg");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(loadavg_proc.getInputStream()));
			String line;
			
			_out.write("<<<cpu>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
			
			Iterator<Map.Entry<String,Integer>> it = netstatVal.entrySet().iterator();
		    while (it.hasNext()) {
		    	Map.Entry<String,Integer> pairs = (Map.Entry<String,Integer>)it.next();
		        _out.write(pairs.getKey()+" "+pairs.getValue()+NEWLINE);
//		        System.out.println(pairs.getKey() + " = " + pairs.getValue());
//		        it.remove(); // avoids a ConcurrentModificationException
		    }
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
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
		_out.write("    Radio Version: "+Build.getRadioVersion()+NEWLINE);
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
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}	
	}
	
	private void writeProcRank(PrintWriter _out)
	{
		Process procrank_proc;
		try {
			procrank_proc = Runtime.getRuntime().exec("procrank");
			
			BufferedReader is = new BufferedReader(new InputStreamReader(procrank_proc.getInputStream()));
			String line;
			
			_out.write("<<<procrank>>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}		
	}
	
	/* Maybe run this as root?
	 * http://stackoverflow.com/questions/4905743/android-how-to-gain-root-access-in-an-android-application
	 */
	private void writeAppMemUsage(PrintWriter _out, String _app)
	{
		Process dumpsys_proc;
		try {
			dumpsys_proc = Runtime.getRuntime().exec("dumpsys meminfo "+_app);
			
			BufferedReader is = new BufferedReader(new InputStreamReader(dumpsys_proc.getInputStream()));
			String line;
			
			_out.write("<<<mem_"+_app+">>>"+NEWLINE);
//			is.readLine(); // skip the first line
			while ((line = is.readLine()) != null)
			{
				_out.write(line+NEWLINE);
			}
			
			is.close();
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}		
	}
	
	private int parseHRNumber(String _hrNum)
	{
//		String multiplier = _hrNum.substring(_hrNum.length()-1);
		int num = 0;
		try
		{
			num=Integer.parseInt(_hrNum.substring(0,_hrNum.length()-1));
		}
		catch(NumberFormatException _nfex)
		{
			Log.e(TAG,_hrNum);
		}
		return num;
	}
	
	
	
	private static boolean copyAssetFolder(AssetManager assetManager,
			String fromAssetPath, String toPath) {
		try {
			String[] files = assetManager.list(fromAssetPath);
			new File(toPath).mkdirs();
			boolean res = true;
			for (String file : files)
				if (file.contains("."))
					res &= copyAsset(assetManager, fromAssetPath + "/" + file,
							toPath + "/" + file);
				else
					res &= copyAssetFolder(assetManager, fromAssetPath + "/"
							+ file, toPath + "/" + file);
			return res;
		} catch (Exception e) {
			Log.e(TAG,e.getMessage());
			return false;
		}
	}

	private static boolean copyAsset(AssetManager assetManager,
			String fromAssetPath, String toPath) {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = assetManager.open(fromAssetPath);
			new File(toPath).createNewFile();
			out = new FileOutputStream(toPath);
			copyFile(in, out);
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
			return true;
		} catch (Exception e) {
			Log.e(TAG,e.getMessage());
			return false;
		}
	}

	private static void copyFile(InputStream in, OutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}
	
	private void changeBusyboxPermission()
	{
		try
		{
			Runtime.getRuntime().exec("/system/bin/chmod 777 "+getApplicationInfo().dataDir+"/busybox");
			Runtime.getRuntime().exec("/system/bin/chmod 777 "+getApplicationInfo().dataDir+"/busybox-i686");
			Runtime.getRuntime().exec("/system/bin/chmod 777 "+getApplicationInfo().dataDir+"/busybox-x86_64");
		}
		catch (IOException e)
		{
			Log.e(TAG,e.getMessage());
		}
	}
}
