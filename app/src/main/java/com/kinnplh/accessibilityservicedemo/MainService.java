package com.kinnplh.accessibilityservicedemo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainService extends AccessibilityService {
    private List<String> textsToClickForOpenInGivenApp;
    private static final String MY_APP_NAME = "QQ";

    public static MainService instance;

    private String lastToastResult;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        textsToClickForOpenInGivenApp = new ArrayList<>();
        textsToClickForOpenInGivenApp.add("其他应用打开");
        textsToClickForOpenInGivenApp.add(MY_APP_NAME);
        textsToClickForOpenInGivenApp.add("仅一次");
    }

    private String lastPackageName;
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN){
            Runnable run = () -> {
                lastPackageName = Utility.getCurrentPackageName(this);
                boolean result = Utility.clickByTextList(this, textsToClickForOpenInGivenApp);
                Utility.runInMain(this, ()-> Toast.makeText(this, result? "成功": "失败", Toast.LENGTH_SHORT).show());
                if(result){
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Utility.bringAppToFrontByPackageName(this, lastPackageName);
                }
            };

            new Thread(run).start();
        } else if(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN && event.getAction() == KeyEvent.ACTION_DOWN){
            Runnable runnable = ()->{
                String result = autoSaveImg();
                Log.i("res", "Toast result " + result);
                Utility.runInMain(this, ()->Toast.makeText(this, result == null? "保存失败": "【保存结果】 " + result, Toast.LENGTH_LONG).show());
            };

            new Thread(runnable).start();
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        for(Object key: Utility.hasPageChangeEvent.keySet()){
            Utility.hasPageChangeEvent.put(key, true);
        }

        if(event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED){
            synchronized (MAIN_CB_MUTEX){
                lastToastResult = String.valueOf(event.getText().get(0));
                MAIN_CB_MUTEX.notify();
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private static final Integer AUTO_SAVE_IMG_MUTEX = "AUTO_SAVE_IMG_MUTEX".hashCode();  // 禁止同时调用函数 autoSaveImg
    private static final Integer CB_FUNC_MUTEX = "CB_FUNC_MUTEX".hashCode();  // cb与函数同步
    private static final Integer MAIN_CB_MUTEX = "MAIN_CB_MUTEX".hashCode(); // 主线程与cb同步

    private String autoSaveImg(){
        synchronized (AUTO_SAVE_IMG_MUTEX){
            Utility.assertTrue(!Utility.isMainThread());
            Utility.longPressWindowCenter(1000, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    // 主线程调用
                    super.onCompleted(gestureDescription);
                    new Thread(){
                        @Override
                        public void run() {
                            boolean result = Utility.clickByTextList(MainService.instance, Collections.singletonList("保存图片"));
                            if(!result){
                                synchronized (CB_FUNC_MUTEX){
                                    lastToastResult = null;
                                    CB_FUNC_MUTEX.notify();
                                }
                                return;
                            }
                            synchronized (MAIN_CB_MUTEX){
                                try {
                                    MAIN_CB_MUTEX.wait(2000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                            synchronized (CB_FUNC_MUTEX){
                                CB_FUNC_MUTEX.notify();
                            }
                        }
                    }.start();
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    // 主线程调用
                    super.onCancelled(gestureDescription);
                    synchronized (CB_FUNC_MUTEX){
                        lastToastResult = null;
                        CB_FUNC_MUTEX.notify();
                    }
                }
            });

            synchronized (CB_FUNC_MUTEX){
                try {
                    CB_FUNC_MUTEX.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return lastToastResult;
        }
    }
}
