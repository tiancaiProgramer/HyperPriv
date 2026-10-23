package io.github.hyperpriv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public final class MainActivity extends Activity {
    private LinearLayout body;
    private static final int BG=0xfff5f5f0,INK=0xff17251e,MUTED=0xff637067,GREEN=0xff267346;
    @Override public void onCreate(Bundle b){super.onCreate(b);Settings.changed=()->runOnUiThread(this::render);render();}
    @Override public void onResume(){super.onResume();render();}
    @Override public void onDestroy(){Settings.changed=null;super.onDestroy();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout box,String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));
        if(bold)t.setTypeface(null,Typeface.BOLD);box.addView(t);return t;
    }
    private LinearLayout card(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(18),dp(12),dp(18),dp(14));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(18));box.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);body.addView(box,p);return box;
    }
    private void render(){
        if(isFinishing()||isDestroyed())return;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(20),dp(20),dp(30));scroll.addView(body);setContentView(scroll);
        text(body,"HYPERPRIV  /  0.2.0",12,GREEN,true);text(body,"隐私与限制调整",26,INK,true);
        text(body,"HyperOS 3 · 当前设备适配版",14,MUTED,false);
        LinearLayout status=card();text(status,Settings.remote!=null?"LSPosed 已连接":"等待启用模块",18,INK,true);text(status,Settings.status,13,MUTED,false);
        text(status,"作用域：安装器、安全中心、安全组件、统计、设置、电量与性能、Joyose、MSA。设置保存后重新打开对应应用生效。",13,MUTED,false);
        text(status,"移除授权读秒后，仍需自行确认；开关开启表示屏蔽该行为。",13,MUTED,false);
        LinearLayout current=null;String section="";
        for(int i=0;i<Options.KEYS.length;i++){
            if(!section.equals(Options.SECTIONS[i])){section=Options.SECTIONS[i];current=card();text(current,section,17,INK,true);}
            final String key=Options.KEYS[i];Switch sw=new Switch(this);sw.setText(Options.LABELS[i]);sw.setTextSize(15);sw.setTextColor(INK);sw.setMinHeight(dp(48));
            sw.setChecked(Settings.local(this).getBoolean(key,true));sw.setContentDescription(Options.LABELS[i]);
            sw.setOnCheckedChangeListener((v,on)->{Settings.set(this,key,on);Toast.makeText(this,Settings.remote==null?"已本地保存，启用模块后同步":"已保存",Toast.LENGTH_SHORT).show();});
            current.addView(sw);text(current,Options.DETAILS[i],12,MUTED,false);
        }
        LinearLayout limits=card();text(limits,"本版未提供",17,INK,true);text(limits,"AVB 启动校验在应用进程之外执行，无法由此模块移除。真实电池容量限制尚未定位；本模块不伪造容量，也不修改充电电压或电池固件。",12,MUTED,false);
        LinearLayout about=card();text(about,"适配与恢复",17,INK,true);
        for(String pkg:Options.TARGETS){try{text(about,pkg+"\n"+getPackageManager().getPackageInfo(pkg,0).versionName,12,MUTED,false);}catch(Exception e){text(about,pkg+" · 未安装",12,MUTED,false);}}
        text(about,"仅对上面列出的组件版本生效。系统组件升级后，对应功能会暂停，需重新适配。",12,MUTED,false);
        Button restore=new Button(this);restore.setText("关闭全部功能");restore.setOnClickListener(v->{Settings.set(this,"master",false);render();Toast.makeText(this,"已关闭。重新打开对应应用恢复系统行为。",Toast.LENGTH_LONG).show();});about.addView(restore);
        Button ref=new Button(this);ref.setText("参考项目 HyperCeiler");ref.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/ReChronoRain/HyperCeiler"))));about.addView(ref);
        text(about,"AGPL-3.0 · 源码随安装包提供。模块不申请网络权限。部分新功能尚待实际场景验证。此版本只处理列明入口，不代表关闭系统全部数据采集。",12,MUTED,false);
    }
}
