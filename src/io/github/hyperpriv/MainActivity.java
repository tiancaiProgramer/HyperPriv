package io.github.hyperpriv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** NoAdMi-style cards: group, inline submenu, then individual options. */
public final class MainActivity extends Activity {
    private static final int BG=0xfff7f6f1, INK=0xff242c28, MUTED=0xff66706a, ACCENT=0xffca581b;
    private final List<Runnable> refreshers=new ArrayList<>();
    private SharedPreferences ui;
    private LinearLayout content;
    private ScrollView scroll;
    private boolean updating;
    private long lastSaveNotice=-3000;
    private int restoredScroll;
    private final Runnable settingsChanged=()->runOnUiThread(this::refresh);

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ui=getSharedPreferences("hyperpriv_ui",MODE_PRIVATE);
        restoredScroll=state==null?0:state.getInt("scroll_y",0);
        Settings.changed=settingsChanged;
        render();
    }
    @Override public void onResume(){super.onResume();Settings.changed=settingsChanged;Settings.refreshScopes();refresh();}
    @Override public void onSaveInstanceState(Bundle out){
        out.putInt("scroll_y",scroll==null?0:scroll.getScrollY());super.onSaveInstanceState(out);
    }
    @Override public void onDestroy(){if(Settings.changed==settingsChanged)Settings.changed=null;super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int gap(int value){return Math.round(value*0.75f*getResources().getDisplayMetrics().density);}
    private LinearLayout vertical(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private TextView label(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setLineSpacing(gap(4),1);text.setPadding(0,gap(5),0,gap(5));
        if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    private GradientDrawable background(int color,int radius){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(radius));return bg;
    }
    private LinearLayout card(){
        LinearLayout box=vertical();box.setPadding(dp(20),gap(14),dp(20),gap(14));box.setBackground(background(Color.WHITE,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=gap(16);content.addView(box,lp);return box;
    }
    private void clickable(TextView text){
        text.setMinHeight(dp(48));text.setGravity(Gravity.CENTER_VERTICAL);text.setFocusable(true);
        android.util.TypedValue value=new android.util.TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true))text.setBackgroundResource(value.resourceId);
    }
    private boolean selected(String key){return Settings.local(this).getBoolean(key,Options.defaultValue(key));}
    private void refresh(){
        if(isFinishing()||isDestroyed())return;
        updating=true;
        try{for(Runnable r:refreshers)r.run();}finally{updating=false;}
    }
    @SuppressWarnings("deprecation") private void render(){
        refreshers.clear();
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                |View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setClipToPadding(true);
        content=vertical();content.setPadding(dp(24),gap(24),dp(24),gap(28));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets system=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                left=system.left;top=system.top;right=system.right;bottom=system.bottom;
            }else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}
            scroll.setPadding(left,top,right,bottom);return insets;
        });
        scroll.requestApplyInsets();
        String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        label(content,"HYPERPRIV  /  "+version,12,ACCENT,true);
        label(content,"HyperPriv",30,INK,true);label(content,"按需调整，少些打扰。",14,MUTED,false);
        statusCard();
        for(UiCatalog.Group group:UiCatalog.GROUPS)featureCard(group);
        aboutCard();
        refresh();
        if(restoredScroll>0)scroll.post(()->scroll.scrollTo(0,restoredScroll));
    }
    private void statusCard(){
        LinearLayout box=card();
        TextView title=label(box,"",17,INK,true);
        TextView message=label(box,"",13,MUTED,false);
        TextView service=label(box,"",12,ACCENT,false);
        toggle(box,"启用 HyperPriv","master",null,true);
        refreshers.add(()->{
            title.setText(Settings.remote!=null?"框架服务已连接":"等待框架服务连接");
            message.setText(Settings.remote==null?"请在 LSPosed 启用模块并勾选推荐作用域；未连接时可先保存设置。"
                    :selected("master")?"展开分类与小菜单设置；保存后请重新启动对应应用进程。":"总开关已关闭，下面的独立设置仍会保留。");
            service.setText(Settings.status+"\n"+Settings.scopeStatus());
        });
    }
    private void fold(TextView header,LinearLayout body,String id,String title){
        Runnable update=()->{
            boolean expanded=ui.getBoolean(id,false);

            header.setText((expanded?"▾  ":"▸  ")+title);
            header.setContentDescription(title+(expanded?"，点击收起":"，点击展开"));
            body.setVisibility(expanded?View.VISIBLE:View.GONE);
        };
        header.setOnClickListener(v->{ui.edit().putBoolean(id,!ui.getBoolean(id,false)).apply();update.run();});
        refreshers.add(update);
    }
    private static final class AppState {
        final boolean supported;final String text;final long version;
        AppState(boolean supported,String text){this(supported,text,-1);}
        AppState(boolean supported,String text,long version){this.supported=supported;this.text=text;this.version=version;}
    }
    private AppState appState(String pkg){
        boolean compatibility=selected(Compatibility.KEY);
        if(pkg.equals("android")){
            boolean strict=Compatibility.strictSystem(Build.VERSION.SDK_INT,Build.FINGERPRINT);
            return new AppState(strict||compatibility,strict?"Android 16 · 已匹配本机构建，首次加载需重启":compatibility?"实验兼容 · 当前系统构建未经验证":"系统构建未适配",Build.VERSION.SDK_INT);
        }
        try{
            PackageInfo info=getPackageManager().getPackageInfo(pkg,0);long version=info.getLongVersionCode();boolean supported=false;
            for(Rule rule:Profiles.ALL)if(rule.pkg.equals(pkg)&&rule.version==version){supported=true;break;}
            for(Rule rule:ExtraTargets.ALL)if(rule.pkg.equals(pkg)&&rule.version==version){supported=true;break;}
            boolean trial=compatibility&&(!Compatibility.select(Profiles.ALL,pkg,version,true).isEmpty()||!Compatibility.select(ExtraTargets.ALL,pkg,version,true).isEmpty());
            String status=!supported?(trial?"实验兼容 · 版本未经验证":"未适配"):Profiles.apkHash(pkg,version)!=null?(compatibility?"实验兼容 · 放宽构建校验":"启用时核对构建"):"版本已适配";
            return new AppState(supported||trial,"已安装 "+info.versionName+" · "+status,version);
        }catch(Exception e){return new AppState(false,"未安装");}
    }
    private LinearLayout appGroup(String id,String title){
        LinearLayout box=card();TextView header=label(box,"",21,INK,true);clickable(header);
        header.setPadding(0,gap(12),0,gap(12));
        LinearLayout body=vertical();box.addView(body,new LinearLayout.LayoutParams(-1,-2));
        fold(header,body,"group_"+id,title);return body;
    }
    private void coreSettings(LinearLayout box){
        boolean strict=Compatibility.strictSystem(Build.VERSION.SDK_INT,Build.FINGERPRINT);
        boolean supported=strict||selected(Compatibility.KEY);
        label(box,strict?"已适配当前系统构建。首次使用需勾选系统框架并重启手机。":supported?"实验兼容：当前构建未经验证，逐项尝试现有方法；系统层需重启后检查加载日志。":"当前系统构建尚未适配，可在关于中按需开启实验兼容模式。",12,ACCENT,false);
        label(box,"新选项默认关闭，请按需开启。",12,MUTED,false);
        toggle(box,"启用核心破解","core_enabled","同时受上方模块总开关控制。",supported);
        toggle(box,"允许降级安装","core_downgrade","保留应用数据安装旧版，可能存在数据兼容问题。",supported);
        toggle(box,"允许不同签名覆盖安装","core_update_signature","允许覆盖安装不同签名的同名应用。",supported);
        toggle(box,"跳过 APK 完整性校验","core_apk_integrity","放宽已识别的 APK 内容与签名方案检查。",supported);
        toggle(box,"允许不同签名的分包","core_split_signature","允许同一应用的分包使用不同签名。",supported);
        toggle(box,"停用系统安装验证代理","core_verification_agent","跳过系统安装验证代理的检测。",supported);
        toggle(box,"解除 resources.arsc 限制","core_resources","允许压缩或未对齐的 APK 资源表。",supported);
        for(ExtraFeatures.Feature extra:ExtraFeatures.ALL)if(extra.key.startsWith("core_"))toggle(box,extra.title,extra.key,extra.note,supported&&Compatibility.supports(ExtraTargets.ALL,extra.key,"android",Build.VERSION.SDK_INT,selected(Compatibility.KEY)));
    }
    private void launcherIconControl(LinearLayout box){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,gap(12),0,gap(12));
        LinearLayout copy=vertical();label(copy,"显示桌面图标",15,INK,true);
        label(copy,"隐藏后仍可从 LSPosed 的模块设置打开。",12,MUTED,false);
        LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-2,1);textParams.rightMargin=dp(12);row.addView(copy,textParams);
        Switch control=new Switch(this);control.setShowText(false);control.setMinWidth(dp(48));control.setMinHeight(dp(48));control.setContentDescription("显示桌面图标");row.addView(control);box.addView(row);
        ComponentName alias=new ComponentName(this,getPackageName()+".LauncherAlias");
        row.setOnClickListener(v->control.toggle());
        control.setOnCheckedChangeListener((button,checked)->{
            if(updating)return;
            try{
                getPackageManager().setComponentEnabledSetting(alias,checked?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
                Toast.makeText(this,checked?"桌面图标已显示":"桌面图标已隐藏，可从 LSPosed 打开",Toast.LENGTH_SHORT).show();
            }catch(RuntimeException e){Toast.makeText(this,"图标设置未能保存",Toast.LENGTH_SHORT).show();}
            refresh();
        });
        refreshers.add(()->{
            int state=getPackageManager().getComponentEnabledSetting(alias);
            control.setChecked(state!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED&&state!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER&&state!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        });
    }
    private void featureCard(UiCatalog.Group spec){
        LinearLayout box=appGroup(spec.id,spec.title);label(box,spec.detail,12,MUTED,false);
        for(UiCatalog.Section section:spec.sections){
            LinearLayout options=information(box,"section_"+spec.id+"_"+section.id,section.title);
            if(section.id.equals("core")){coreSettings(options);continue;}
            if(section.detail!=null)label(options,section.detail,12,ACCENT,false);
            for(String key:section.keys){
                AppState state=appState(UiCatalog.target(key));String note=UiCatalog.note(key);boolean available=state.supported;
                if(ExtraFeatures.get(key)!=null){String hookKey=key.equals("clipboard_close_once")?"clipboard_toast":key;available=available&&Compatibility.supports(ExtraTargets.ALL,hookKey,UiCatalog.target(key),state.version,selected(Compatibility.KEY));}
                if(available&&state.text.contains("实验兼容"))note=(note==null?"":note+"\n")+"实验兼容：可尝试开启，是否生效以加载与命中日志为准。";
                if(!available)note=(note==null?"":note+"\n")+"对应入口尚未适配或组件未安装，原有设置已保留。";
                toggle(options,UiCatalog.label(key),key,note,available);
            }
            if(section.id.equals("privacy_tools")){
                textOption(options,"后台剪贴板包名白名单","clipboard_packages","每行一个完整包名；保存不会自动开启后台读取。",true);
                opacityOption(options);
                textOption(options,"剪贴板提示关闭按钮文字","clipboard_close_text","留空沿用原文字，最多 20 个字。",false);
            }
        }
    }
    private void toggle(LinearLayout parent,String title,String key,String note,boolean available){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,gap(12),0,gap(12));row.setMinimumHeight(dp(48));
        LinearLayout copy=vertical();LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.rightMargin=dp(12);row.addView(copy,copyParams);
        TextView name=label(copy,title,14,INK,false);name.setPadding(0,0,0,0);
        if(note!=null)label(copy,note,12,MUTED,false);
        Switch control=new Switch(this);control.setShowText(false);control.setContentDescription(title);control.setMinHeight(dp(48));control.setMinWidth(dp(48));
        row.addView(control,new LinearLayout.LayoutParams(-2,-2));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        row.setOnClickListener(v->{if(control.isEnabled())control.toggle();});
        control.setOnCheckedChangeListener((button,checked)->{
            if(updating)return;
            Settings.set(this,key,checked);
            if(key.equals(Compatibility.KEY)){restoredScroll=scroll==null?0:scroll.getScrollY();render();}else refresh();
            long now=SystemClock.elapsedRealtime();
            if(now-lastSaveNotice>=2500){
                lastSaveNotice=now;
                Toast.makeText(this,Settings.remote==null?"已保存到本机，连接框架后同步":key.equals(Compatibility.KEY)?"已保存，请重启目标应用；系统层兼容模式变化需重启手机":key.startsWith("core_")?"已保存；系统框架首次加载需重启手机":"已保存，请重新启动对应应用进程",Toast.LENGTH_SHORT).show();
            }
        });
        refreshers.add(()->{control.setChecked(selected(key));control.setEnabled(available);name.setTextColor(available?INK:MUTED);});
    }
    private void textOption(LinearLayout parent,String title,String key,String note,boolean multiline){
        label(parent,title,14,INK,true);label(parent,note,12,MUTED,false);
        EditText input=new EditText(this);input.setTextSize(14);input.setTextColor(INK);input.setText(Settings.local(this).getString(key,""));input.setContentDescription(title);input.setSingleLine(!multiline);input.setMinLines(multiline?2:1);parent.addView(input,new LinearLayout.LayoutParams(-1,-2));
        TextView save=label(parent,multiline?"保存白名单":"保存按钮文字",14,ACCENT,true);clickable(save);
        save.setOnClickListener(v->{try{Settings.setText(this,key,input.getText().toString().trim());input.setText(Settings.local(this).getString(key,""));Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
    }
    private void opacityOption(LinearLayout parent){
        int initial=Math.max(0,Math.min(100,Settings.local(this).getInt("clipboard_opacity",100)));
        TextView title=label(parent,"剪贴板提示不透明度："+initial+"%",14,INK,true);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(initial);slider.setContentDescription("剪贴板提示不透明度");parent.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){title.setText("剪贴板提示不透明度："+value+"%");}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){Settings.setOpacity(MainActivity.this,bar.getProgress());Toast.makeText(MainActivity.this,"已保存",Toast.LENGTH_SHORT).show();}
        });
    }
    private LinearLayout information(LinearLayout parent,String id,String title){
        TextView header=label(parent,"",16,INK,true);clickable(header);header.setPadding(0,gap(12),0,gap(12));
        LinearLayout body=vertical();parent.addView(body,new LinearLayout.LayoutParams(-1,-2));fold(header,body,"info_"+id,title);return body;
    }
    private void link(LinearLayout parent,String label,String url){
        TextView target=label(parent,label+"  ↗",15,ACCENT,true);clickable(target);target.setContentDescription(label+"，在浏览器打开");
        target.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show();}});
    }
    private void aboutCard(){
        LinearLayout box=appGroup("about","关于");
        launcherIconControl(box);
        toggle(box,"实验兼容模式",Compatibility.KEY,"默认关闭。开启后放宽机型、版本及设置构建校验，尝试现有规则，沿用各功能当前开关。方法匹配不代表行为正确；缺失方法跳过，不会补齐尚未实现的功能。切换后重启目标进程，系统层需重启手机。",true);
        link(box,"检查更新","https://github.com/tiancaiProgramer/HyperPriv/releases");
        label(box,"tiancaiProgramer/HyperPriv · 安装包与更新说明",12,MUTED,false);
        LinearLayout usage=information(box,"usage","使用与恢复");
        label(usage,"开关开启表示执行对应调整；关闭单项后恢复原方法。关闭总开关不会清除独立设置。",12,MUTED,false);
        label(usage,"应用功能需勾选对应的九个应用作用域；新增自启动与剪贴板提示需要权限管理作用域。系统层功能还需勾选系统框架并重启手机完成首次加载。",12,MUTED,false);
        label(usage,"停用或卸载本模块后重启目标进程，也可恢复原方法。手动开启的 USB 设置或选择的锁屏时间需自行恢复。",12,MUTED,false);
        TextView disable=label(usage,"关闭模块总开关",14,ACCENT,true);clickable(disable);
        disable.setOnClickListener(v->{Settings.set(this,"master",false);refresh();Toast.makeText(this,"总开关已关闭，独立设置已保留",Toast.LENGTH_SHORT).show();});
        LinearLayout versions=information(box,"versions","组件适配说明");
        for(UiCatalog.Component app:UiCatalog.COMPONENTS){
            label(versions,app.title,14,INK,true);label(versions,appState(app.pkg).text,12,MUTED,false);
            label(versions,"已核对版本 "+UiCatalog.versions(app.pkg),12,MUTED,false);
        }
        label(versions,"默认模式另核对设置安装包构建；实验兼容模式放宽此校验。这里的版本信息不代表规则已实际命中。",12,MUTED,false);
        LinearLayout limits=information(box,"limits","验证进度与功能边界");
        label(limits,"尚未完成无账号 / 无 SIM / 离线 USB 开启、高温效果、安装完成页和整机抓包验证。上传入口屏蔽不代表关闭全部系统采集。",12,MUTED,false);
        label(limits,"核心破解曾在参考设备核对方法签名；实验兼容不代表当前设备已完成系统注入或安装验证。完整记录见功能说明。",12,MUTED,false);
        label(limits,"新增系统层功能默认关闭。共享 UID、沿用原签名、未知来源安装与权限放宽需按需手动开启；实际效果仍待对应场景验证。",12,MUTED,false);
        label(limits,"本版未提供 AVB 移除与真实电池锁容解除，也不修改充电电压或电池固件。",12,MUTED,false);
        link(limits,"查看逐项功能说明","https://github.com/tiancaiProgramer/HyperPriv/blob/main/FEATURES.md");
        LinearLayout source=information(box,"source","项目与许可");
        link(source,"HyperPriv 源码","https://github.com/tiancaiProgramer/HyperPriv");
        link(source,"参考项目 HyperCeiler","https://github.com/ReChronoRain/HyperCeiler");
        label(source,"AGPL-3.0 · 模块无网络权限。界面参考 NoAdMi；核心破解入口参考 HyperCeiler 与 CorePatch 的设计。",12,MUTED,false);
    }
}
