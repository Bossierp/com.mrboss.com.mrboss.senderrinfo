/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package com.mrboss.senderrinfo;

import org.json.JSONArray;
import org.json.JSONException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import android.os.Bundle;
import org.apache.cordova.*;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import android.content.ContentValues;
import android.database.Cursor;

import org.apache.cordova.LOG;
import android.content.Intent;
import android.provider.Settings;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.BufferedReader;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.io.InputStreamReader;
import java.io.BufferedWriter;
import java.io.FileWriter;

import android.media.*;

public class SendErrInfo extends CordovaPlugin {
	private SendErrInfo senderrinfo = this;
	private Context context;       //app对象
	private Context basecontext;  //重启目标对象
	private ExceptionHandle eHandle = new ExceptionHandle();
	//private String strUrl = "http://192.168.1.98:3188/ErrorAPI.aspx";  //接收异常信息的服务器地址
	private String strUrl = "http://saasus.mr-boss.net:402/ErrorAPI.aspx";
	
  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    context = cordova.getActivity().getApplicationContext();
	basecontext = cordova.getActivity().getBaseContext();
	
	if ("SendErrInfo_Action".equals(action)) {
      try {
		  new Thread(){
            @Override
            public void run() {
                checkErrorInfo();
            }
          }.start();
		  
		  CrashHandler crashHandler = new CrashHandler();
		  Thread.setDefaultUncaughtExceptionHandler(crashHandler);
		  //Log.d("errorInfo", "启动插件SendErrInfo");
      } catch (Exception e) {
        return false;
      }
      callbackContext.success();
      return true;
    }
    return false;
  }
  
  
    /**
     * 检查ErrorInfo文件夹下是否有错误信息文件
     */
    public void checkErrorInfo(){
        String path = "/data/data/com.mrboss.offlineposapp/ErrorInfo"; // 错误信息目录
        File f = new File(path);
        if (!f.exists()) {
            return;
        }
        File fa[] = f.listFiles();
        for (int i = 0; i < fa.length; i++) {
            File fs = fa[i];
            if (fs.isDirectory()) {
                continue;
            } else {
                //Log.d("errorInfo", fs.getName());
                if (sendErrFile(fs)){
                    fs.delete();
                    //Log.d("errorInfo", "删除error文件");
                }
            }
        }
    }
	
	/**
     * 发送错误信息文件
     */
    private boolean sendErrFile(File fs) {
        URL url = null;
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;
        String errInfo = "";
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(fs)));
            while ((line = br.readLine()) != null) {
				sb.append(line + "\r\n");
                //sb.append(line);
            }
            errInfo = sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (br != null){
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String dataTime = getDataTimeNow();
        String mac = getMacAdress();
		//String strUrl = "http://saasus.mr-boss.net:402/ErrorAPI.aspx";  //接收异常信息的服务器地址
		String params = "Mac=" + mac + "&DateTime=" + dataTime + "&ErrInfo=" + errInfo;
        //Log.d("errInfo", errInfo);

        try {
            url = new URL(strUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            //conn.setRequestProperty("Connection", "Keep-Alive");
			conn.setConnectTimeout(10*1000);  //10秒超时
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            // 获取URLConnection对象对应的输出流
            PrintWriter printWriter = new PrintWriter(conn.getOutputStream());
            // 发送请求参数
            printWriter.write(params);  //post的参数 xx=xx&yy=yy
            // flush输出流的缓冲
            printWriter.flush();
            conn.getInputStream();

            //获得返回信息,成功则删除fs,不成功再发一次
            //Log.d("errInfo", "已送错误信息到服务器");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }
	
	/**
     * 获取mac地址
     * @return
     */
    public String getMacAdress(){
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifi.getConnectionInfo();
        return info.getMacAddress();
    }
	
	/**
     * 获取系统当前时间
     * @return
     */
    public String getDataTimeNow(){
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        return dateFormat.format(now).toString();
    }
	
	/**
	* 主程序出异常,执行处理类
	*/
	class CrashHandler implements Thread.UncaughtExceptionHandler{
		@Override
		public void uncaughtException(Thread thread, Throwable e) {
			eHandle.handle(e);  //联网发送错误信息

			try {
				Thread.sleep(4000);
			} catch (InterruptedException e1) {
			}
			//执行过4秒后自动重启操作
			//Intent to restart application
			Intent intent = basecontext.getPackageManager().getLaunchIntentForPackage(basecontext.getPackageName());
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			cordova.getActivity().finish();
			senderrinfo.cordova.startActivityForResult((CordovaPlugin) senderrinfo, intent, 0);
		}
		
	}
	
	/**
	* 统一处理异常工具类
	*/
	class ExceptionHandle {
		/**
		* 处理方式
		* @param e (Error,Exception的父类)
		*/
		public void handle(Throwable e){
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String errorInfo = sw.toString();
			//Log.d("errorInfo", errorInfo);
			writeFile(errorInfo);
		}
		
		/**
		* 将异常信息记录到error.txt
		* @param errorInfo
		*/
		public void writeFile(String errorInfo){
			String fileName = "error.txt";
			File exportDir = new File("/data/data/com.mrboss.offlineposapp/", "ErrorInfo");
			if (!exportDir.exists()) {
				exportDir.mkdirs();
			}
			File txtInfo = new File(exportDir + "/" + fileName);
			if(!txtInfo.exists()){
				try {
					//Log.d("errorInfo", "新建log文件");
					txtInfo.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			byte bytes[] = new byte[1024*8];
			errorInfo = errorInfo.replaceAll("\n", "\r\n");
			bytes = errorInfo.getBytes();
			int b = bytes.length;
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(txtInfo, true);
				BufferedWriter bw = new BufferedWriter(new FileWriter(txtInfo, true));
				bw.append("\r\n添加ErrorInfo--------------------\r\n");
				bw.close();
				fos.write(bytes, 0, b);
				fos.write(bytes);
				fos.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	
}