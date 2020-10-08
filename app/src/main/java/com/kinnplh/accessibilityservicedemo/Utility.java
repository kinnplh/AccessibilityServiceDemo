package com.kinnplh.accessibilityservicedemo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class Utility {
    public static void assertTrue(boolean cond) {
        if (BuildConfig.DEBUG && !cond) {
            throw new AssertionError("Assertion failed");
        }
    }

    public static boolean isMainThread(){
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static void runInMain(Context context, Runnable runnable){
        android.os.Handler handler = new Handler(context.getMainLooper());
        handler.post(runnable);
    }
    public static boolean launchAppByPackageName(Context context, String packageName){
        if(packageName == null || context == null){
            return false;
        }

        PackageManager manager = context.getPackageManager();
        PackageInfo info;
        try {
            info = manager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            info = null;
            e.printStackTrace();
        }

        if(info == null)
            return false;
        Intent intent = manager.getLaunchIntentForPackage(packageName);
        if(intent == null)
            return false;
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent); // 非阻塞。
        return true;
    }

    public static boolean bringAppToFrontByPackageName(Context context, String packageName){
        if(packageName == null || context == null){
            return false;
        }

        PackageManager manager = context.getPackageManager();
        PackageInfo info;
        try {
            info = manager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            info = null;
            e.printStackTrace();
        }

        if(info == null)
            return false;
        Intent intent = manager.getLaunchIntentForPackage(packageName);
        if(intent == null)
            return false;
        // intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        // intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(intent); // 非阻塞。
        return true;
    }
    public interface WaitUntilCondition<T> {
        Pair<Boolean, T> accept();
    }

    public static Map<Object, Boolean> hasPageChangeEvent = new ConcurrentHashMap<>();
    public static <T> Pair<Boolean, T> waitUntil(WaitUntilCondition<T> cond, long maxTime, long sleepTime){
        Utility.assertTrue(!isMainThread());

        Object o = new Object();
        long startTime = System.currentTimeMillis();
        while (true){
            if(System.currentTimeMillis() - startTime > maxTime){
                return new Pair<>(false, null);
            }

            hasPageChangeEvent.put(o, false);
            Pair<Boolean, T> acceptResult = cond.accept();
            if(acceptResult.first){
                hasPageChangeEvent.remove(o);
                return acceptResult;
            }

            if(sleepTime > 0){
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if(!hasPageChangeEvent.get(o)){
                hasPageChangeEvent.remove(o);
                return new Pair<>(false, null);
            }
        }
    }


    public static boolean clickByTextList(AccessibilityService service, List<String> texts){
        for(String t: texts){
            Pair<Boolean, AccessibilityNodeInfo> searchResult = waitUntil(()->{
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if(root == null){
                    return new Pair<>(false, null);
                }
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
                root.recycle();
                AccessibilityNodeInfo result = null;
                for(AccessibilityNodeInfo n: nodes){
                    if(result == null){
                        AccessibilityNodeInfo clickableNode = getClickableParent(n);
                        if(clickableNode != null){
                            result = clickableNode;
                        }
                    }
                    n.recycle();
                }
                return new Pair<>(result != null, result);
            }, 3000, 100);

            if(!searchResult.first){
                return false;
            }
            boolean clickResult = searchResult.second.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            searchResult.second.recycle();
            if(!clickResult){
                return false;
            }
        }

        return true;
    }

    public static AccessibilityNodeInfo getClickableParent(AccessibilityNodeInfo node){
        AccessibilityNodeInfo crt = AccessibilityNodeInfo.obtain(node);
        while (crt != null){
            if(crt.isClickable()){
                return crt;
            }

            AccessibilityNodeInfo old = crt;
            crt = crt.getParent();
            old.recycle();
        }

        return null;
    }

    public static String printTree(AccessibilityNodeInfo root, String indent, String newLine){
        StringBuffer res = new StringBuffer();
        printNodeStructure(root, 0, res, indent, newLine);
        return res.toString();
    }

    public static String printTree(AccessibilityNodeInfo root){
        return printTree(root, "\t", "\n");
    }


    public static void printNodeStructure(AccessibilityNodeInfo root, int depth, StringBuffer res, String indent, String newLine){
        if(root == null){
            return;
        }
        Rect border = new Rect();
        root.getBoundsInScreen(border);
        for(int i = 0; i < depth; i ++){
            res.append(indent);
        }

        res.append(root.getPackageName()).append(" ")
                .append(root.getClassName()).append(" ")
                .append(root.getViewIdResourceName()).append(" ")
                .append(border.toString()).append(" ")
                .append(root.getText()).append(" ")
                .append(root.getContentDescription()).append(" ")
                .append("isClickable: ").append(root.isClickable()).append(" ")
                .append("isScrollable: ").append(root.isScrollable()).append(" ")
                .append("isVisible: ").append(root.isVisibleToUser()).append(" ")
                .append("isEnabled: ").append(root.isEnabled()).append(" ").append(newLine);

        //res.append(root.toString()).append("\n");
        for(int i = 0; i < root.getChildCount(); ++ i){
            printNodeStructure(root.getChild(i), depth + 1, res, indent, newLine);
        }
    }

    private static AccessibilityService.GestureResultCallback emptyCB = new AccessibilityService.GestureResultCallback() {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
        }
    };

    public static void longPressWindowCenter(long maxTime, AccessibilityService.GestureResultCallback cb){
        if(MainService.instance == null){
            return;
        }
        AccessibilityNodeInfo root = MainService.instance.getRootInActiveWindow();
        if(root == null){
            return;
        }
        longPress(root, maxTime, cb == null? emptyCB: cb);
        root.recycle();
    }

    public static void longPress(AccessibilityNodeInfo node, long maxTime, AccessibilityService.GestureResultCallback cb){
        if(node == null){
            return;
        }
        if(MainService.instance == null){
            return;
        }

        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        int centerX = rect.centerX();
        int centerY = rect.centerY();

        Path longPress1 = new Path();

        longPress1.moveTo(centerX - 1, centerY - 1);
        longPress1.lineTo(centerX - 1, centerY + 1);

        GestureDescription.StrokeDescription strokeDescription1 = new GestureDescription.StrokeDescription(longPress1, 0, maxTime);
        final GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(strokeDescription1);

        new Thread(){
            @Override
            public void run() {
                MainService.instance.dispatchGesture(builder.build(), cb, null);
            }
        }.start();

    }

    public static String getCurrentPackageName(AccessibilityService service){
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if(root == null){
            return null;
        }
        String res = String.valueOf(root.getPackageName());
        root.recycle();
        return res;
    }
}
