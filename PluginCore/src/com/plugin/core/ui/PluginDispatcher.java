package com.plugin.core.ui;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.util.Log;

import com.plugin.core.PluginLoader;
import com.plugin.util.RefInvoker;

import dalvik.system.DexClassLoader;

public class PluginDispatcher {
	
	/**
	 * 在普通的activity中展示插件中的fragment，
	 * 
	 * 因为fragment的宿主Activity是一个普通的activity，所以对目标fragment有特殊要求，
	 * 即fragment中所有需要使用context的地方，都是有PluginLoader.getPluginContext()来获取

	 * @param context
	 * @param target
	 */
	public static void startFragmentWithSimpleActivity(Context context, String targetId) {

		Intent pluginActivity = new Intent();
		pluginActivity.setClass(context, PluginNormalDisplayer.class);
		pluginActivity.putExtra("classId", resloveTarget(targetId));
		pluginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(pluginActivity);
	}
	
	/**
	 * 在重写过Context的activity中展示插件中的fragment，
	 * 
	 * 因为fragment的宿主Activity是重写过的，所以对目标fragment没有特殊要求，无需在fragment中包含任何插件相关的代码
	 * 
	 * 此重写过的activity同样可以展示通过包含PluginLoader.getPluginContext()获取context的fragment
	 * 
	 * @param context
	 * @param target
	 */
	public static void startFragmentWithBuildInActivity(Context context, String targetId) {

		Intent pluginActivity = new Intent();
		pluginActivity.setClass(context, PluginSpecDisplayer.class);
		pluginActivity.putExtra("classId", resloveTarget(targetId));
		pluginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(pluginActivity);
	}
	
	/**
	 * 显示插件中的activity
	 * 
	 *  因为目标activity的宿主Activity是重写过的，所以对目标activity没有特色要求
	 * 
	 * @param context
	 * @param target
	 * 
	 * 放弃代理模式了。采用Activity免注册方式
	 */
	@Deprecated 
	public static void startProxyActivity(Context context, String targetId) {

//		Intent pluginActivity = new Intent();
//		pluginActivity.setClass(context, PluginProxyActivity.class);
//		pluginActivity.putExtra("classId", resloveTarget(targetId));
//		pluginActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		context.startActivity(pluginActivity);
	
	}
	
	public static void startRealService(Context context, Intent intent) {
		if (hackClassLoadIfNeeded(intent)) {
			Intent newIntent = new Intent(context, PluginStubService.class);
			newIntent.putExtra("targetIntent", intent);
			context.startService(newIntent);
		} else {
			context.startService(intent);
		}
	}
	
	private static String resloveTarget(String target) {
		//TODO target到classId的映射
		return target;
	}
	
	private static boolean hackClassLoadIfNeeded(Intent intent) {
		
		String targetClassName = PluginLoader.isMatchPlugin(intent);

		Log.d("PluginDispather", "targetClassName " + targetClassName);
		
		if (targetClassName != null) {
			
			Object mLoadedApk = RefInvoker.getFieldObject(PluginLoader.getApplicatoin(), Application.class.getName(), "mLoadedApk");
			
			ClassLoader originalLoader = (ClassLoader) RefInvoker.getFieldObject(
					mLoadedApk, "android.app.LoadedApk", "mClassLoader");
			
			if (originalLoader instanceof PluginComponentLoader) {
				((PluginComponentLoader)originalLoader).offer(targetClassName);
			} else {
				PluginComponentLoader newLoader = new PluginComponentLoader("", PluginLoader.getApplicatoin().getCacheDir()
						.getAbsolutePath(), PluginLoader.getApplicatoin().getCacheDir().getAbsolutePath(),
						originalLoader);
				newLoader.offer(targetClassName);
				RefInvoker.setFieldObject(mLoadedApk, "android.app.LoadedApk",
						"mClassLoader", newLoader);
			}
			return true;
		}
		return false;
	}

	public static class PluginComponentLoader extends DexClassLoader {

		private final BlockingQueue<String> mServiceClassQueue = new LinkedBlockingQueue<String>();;
		
		public void offer(String className) {
			mServiceClassQueue.offer(className);
		}
		
		public PluginComponentLoader(String dexPath, String optimizedDirectory,
				String libraryPath, ClassLoader parent) {
			super(dexPath, optimizedDirectory, libraryPath, parent);
		}
		
		@Override
		protected Class<?> loadClass(String className, boolean resolve)
				throws ClassNotFoundException {
			
			if (className.equals(PluginStubService.class.getName())) {
				String target = mServiceClassQueue.poll();
				Log.d("PluginAppTrace", "className " + className + ", " + target);
				if (target != null) {
					@SuppressWarnings("rawtypes")
					Class clazz = PluginLoader.loadPluginClassByName(target);
					if (clazz != null) {
						return clazz;
					}
				} 
			}

			return super.loadClass(className, resolve);
		}
		
	}
	
	/**
	public static class PluginComponentLoader extends DexClassLoader {

		private final BlockingQueue<String[]> mServiceClassQueue = new LinkedBlockingQueue<String[]>();;
		
		public void offer(String classId, String className) {
			String[] target = new String[2];
			target[0] = classId;
			target[1] = className;
			mServiceClassQueue.offer(target);
		}
		
		public PluginComponentLoader(String dexPath, String optimizedDirectory,
				String libraryPath, ClassLoader parent) {
			super(dexPath, optimizedDirectory, libraryPath, parent);
		}
		
		@Override
		protected Class<?> loadClass(String className, boolean resolve)
				throws ClassNotFoundException {
			
			if (className.equals(PluginStubService.class.getName())) {
				String[] target = mServiceClassQueue.poll();
				Log.d("PluginDispatcher", "className=" + className + " " + target[0] + ", "
						+ target[1]);
				if (target != null) {
					if (target[0] != null) {
						@SuppressWarnings("rawtypes")
						Class clazz = PluginLoader.loadPluginClassById(target[0]);
						if (clazz != null) {
							return clazz;
						}
					} else if (target[1] != null) {
						@SuppressWarnings("rawtypes")
						Class clazz = PluginLoader.loadPluginClassByName(target[1]);
						if (clazz != null) {
							return clazz;
						}
					}
				} 
			}

			return super.loadClass(className, resolve);
		}
		
	}
	
	private static void replaceClassLoader(String target, String targetClassName) {

		Object mLoadedApk = RefInvoker.getFieldObject(PluginLoader.getApplicatoin(), Application.class.getName(), "mLoadedApk");
		ClassLoader originalLoader = (ClassLoader) RefInvoker.getFieldObject(
				mLoadedApk, "android.app.LoadedApk", "mClassLoader");
		
		if (originalLoader instanceof PluginComponentLoader) {
			((PluginComponentLoader)originalLoader).offer(target, targetClassName);
		} else {
			PluginComponentLoader newLoader = new PluginComponentLoader("", PluginLoader.getApplicatoin().getCacheDir()
					.getAbsolutePath(), PluginLoader.getApplicatoin().getCacheDir().getAbsolutePath(),
					originalLoader);
			newLoader.offer(target, targetClassName);
			RefInvoker.setFieldObject(mLoadedApk, "android.app.LoadedApk",
					"mClassLoader", newLoader);
		}
	}
	**/
}
