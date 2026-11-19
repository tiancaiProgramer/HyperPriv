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
    private String[] pendingKeyShortcut;
    private static final int KEY_SHORTCUT_REQUEST=704;
    private long lastSaveNotice=-3000;
    private int restoredScroll;
    private final Runnable settingsChanged=()->runOnUiThread(this::refresh);

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ui=getSharedPreferences("hyperpriv_ui",MODE_PRIVATE);
        restoredScroll=state==null?0:state.getInt("scroll_y",0);
        pendingKeyShortcut=state==null?null:state.getStringArray("pending_key_shortcut");
        Settings.changed=settingsChanged;
        render();
    }
    @Override public void onResume(){super.onResume();Settings.changed=settingsChanged;RuntimeProfile.initializeUi(this);Settings.refreshScopes();render();}
    @Override public void onPause(){restoredScroll=scroll==null?0:scroll.getScrollY();super.onPause();}
    @Override public void onSaveInstanceState(Bundle out){
        out.putStringArray("pending_key_shortcut",pendingKeyShortcut);
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
    private boolean selected(String key){return Settings.local(this).getBoolean(key,RuntimeProfile.defaultValue(key));}
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
        label(content,RuntimeProfile.eu()?"HyperPriv · EU":"HyperPriv",30,INK,true);label(content,"按需调整，少些打扰。",14,MUTED,false);
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
                    :selected("master")?"展开分类与小菜单设置；保存后重启对应应用；系统框架选项改动后需重启手机。":"总开关已关闭，下面的独立设置仍会保留。");
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
            boolean strict=RuntimeProfile.knownSystem();
            return new AppState(strict||compatibility,strict?"Android 16 · 已匹配本机构建，系统选项改动后需重启":compatibility?"实验兼容 · 当前系统构建未经验证":"系统构建未适配",Build.VERSION.SDK_INT);
        }
        try{
            PackageInfo info=getPackageManager().getPackageInfo(pkg,TelemetryBackups.PACKAGE_FLAGS);long version=info.getLongVersionCode();boolean supported=false;
            if(pkg.equals(HdmiCompatibility.PACKAGE)){
                HdmiCompatibility.Match match=RuntimeProfile.hdmiMatch();
                String status=match==HdmiCompatibility.Match.EXACT?"HDMI 确认入口已核对":
                        match==HdmiCompatibility.Match.EXPERIMENTAL?(compatibility?"实验兼容 · HDMI 确认入口已匹配":"需开启实验兼容模式"):
                        compatibility?"未匹配到 HDMI 确认入口":"需开启实验兼容模式尝试匹配";
                return new AppState(match.allowed(compatibility),"已安装 "+info.versionName+" · "+status,version);
            }
            for(Rule rule:Profiles.ALL)if(rule.pkg.equals(pkg)&&rule.version==version){supported=true;break;}
            for(Rule rule:ExtraTargets.ALL)if(rule.pkg.equals(pkg)&&rule.version==version){supported=true;break;}
            if(pkg.equals(HelperCompatibility.PACKAGE)){
                HelperCompatibility.Match match=HelperCompatibility.resolve(version,EuEnvironment.hash(info.applicationInfo.sourceDir),info.applicationInfo.sourceDir,compatibility);
                return new AppState(match!=null,"已安装 "+info.versionName+" · "+(match==null?"代码未适配":match.experimental?"实验兼容 · "+match.label:"已核对 "+match.label)+(info.applicationInfo.enabled?"":" · 组件已停用"),version);
            }
            boolean trial=compatibility&&(!Compatibility.select(Profiles.ALL,pkg,version,true).isEmpty()||!Compatibility.select(ExtraTargets.ALL,pkg,version,true).isEmpty());
            String status=!supported?(trial?"实验兼容 · 版本未经验证":"未适配"):Profiles.apkHash(pkg,version)!=null?(compatibility?"实验兼容 · 放宽构建校验":"启用时核对构建"):"版本已适配";
            if(RuntimeProfile.eu()&&EuBuild.expectedHash(pkg,version)!=null&&!EuBuild.matches(pkg,version,EuEnvironment.hash(info.applicationInfo.sourceDir))){supported=false;trial=compatibility;status=compatibility?"实验兼容 · EU 构建未经验证":"EU 构建未核对";}
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
        boolean strict=RuntimeProfile.knownSystem();
        boolean supported=strict||selected(Compatibility.KEY);
        label(box,strict?"已适配当前系统构建。勾选系统框架，修改系统选项后重启手机。":supported?"实验兼容：当前构建未经验证，逐项尝试现有方法；系统层需重启后检查加载日志。":"当前系统构建尚未适配，可在关于中按需开启实验兼容模式。",12,ACCENT,false);
        label(box,"新选项默认关闭，请按需开启。",12,MUTED,false);
        for(String key:UiCatalog.CORE_PRIMARY)coreOption(box,key,supported);
        LinearLayout more=information(box,"core_more","更多选项");
        for(String key:UiCatalog.CORE_MORE)coreOption(more,key,supported);
    }
    private void coreOption(LinearLayout box,String key,boolean supported){
        boolean available=supported&&(ExtraFeatures.get(key)==null||Compatibility.supports(ExtraTargets.ALL,key,"android",Build.VERSION.SDK_INT,selected(Compatibility.KEY)));
        toggle(box,UiCatalog.label(key),key,UiCatalog.note(key),available);
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
            if(section.id.equals("keys")){keySettings(options);continue;}
            if(section.detail!=null)label(options,section.detail,12,ACCENT,false);
            if (section.id.equals("reports")) telemetryPackageSettings(options);
            for(String key:section.keys){
                if (TelemetryPackages.managesHook(key)) continue;
                AppState state=appState(UiCatalog.target(key));String note=UiCatalog.note(key);boolean available=state.supported;
                if(ExtraFeatures.get(key)!=null&&!key.equals(HdmiCompatibility.KEY)){String hookKey=key.equals("clipboard_close_once")?"clipboard_toast":key;available=available&&(key.equals("guard_environment")||(Compatibility.supports(ExtraTargets.ALL,hookKey,UiCatalog.target(key),state.version,selected(Compatibility.KEY))||Compatibility.supports(Profiles.ALL,hookKey,UiCatalog.target(key),state.version,selected(Compatibility.KEY))));}
                if(available&&state.text.contains("实验兼容"))note=(note==null?"":note+"\n")+"实验兼容：可尝试开启，是否生效以加载与命中日志为准。";
                if(!available)note=(note==null?"":note+"\n")+(key.equals(HdmiCompatibility.KEY)?state.text+"。":"对应入口尚未适配或组件未安装，原有设置已保留。");
                toggle(options,UiCatalog.label(key),key,note,available);
            }
            if(section.id.equals("privacy_tools")){
                textOption(options,"后台剪贴板包名白名单","clipboard_packages","每行一个完整包名；保存不会自动开启后台读取。",true);
                opacityOption(options);
                textOption(options,"剪贴板提示关闭按钮文字","clipboard_close_text","留空沿用原文字，最多 20 个字。",false);
            }
        }
    }

    private void telemetryPackageSettings(LinearLayout parent) {
        TextView hint = label(parent, "备份并卸载只保存 APK，恢复或卸载需 Root 授权。",
                12, MUTED, false);
        List<View> rows = new ArrayList<>();
        for (TelemetryPackages.Component component : TelemetryPackages.Component.values()) {
            LinearLayout row = vertical();
            row.setPadding(0, gap(9), 0, gap(9));
            parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            rows.add(row);
            label(row, component.title, 14, INK, true);
            TextView status = label(row, "", 12, MUTED, false);
            LinearLayout segments = new LinearLayout(this);
            row.addView(segments, new LinearLayout.LayoutParams(-1, -2));
            java.util.EnumMap<TelemetryPackages.Mode, TextView> controls =
                    new java.util.EnumMap<>(TelemetryPackages.Mode.class);
            for (TelemetryPackages.Mode mode : TelemetryPackages.Mode.values()) {
                if (mode == TelemetryPackages.Mode.HOOK && !component.hasHook()) continue;
                controls.put(mode, segmentButton(segments, mode.label, () -> {
                    if (mode == TelemetryPackages.Mode.UNINSTALL) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("备份并卸载 " + component.title)
                                .setMessage(component.uninstallImpact
                                        + "\n\n将备份 APK 及分包，卸载当前 Android 用户下的组件，并清除该组件的应用数据。可从备份重新安装。")
                                .setPositiveButton("备份并卸载", (dialog, button) -> {
                                    TelemetryPackages.change(this, component, mode);
                                    refresh();
                                })
                                .setNegativeButton("取消", null).show();
                    } else {
                        TelemetryPackages.change(this, component, mode);
                        refresh();
                    }
                }));
            }
            refreshers.add(() -> {
                TelemetryPackages.State state = TelemetryPackages.state(getPackageManager(), component);
                boolean hasBackup = TelemetryBackups.exists(this, component.packageName);
                boolean visible = TelemetryPackages.visible(state, hasBackup);
                row.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (!visible) return;
                boolean compatibility = selected(Compatibility.KEY);
                TelemetryPackages.HookSupport support =
                        TelemetryPackages.hookSupport(this, component, state, compatibility);
                boolean hookSupported = support != TelemetryPackages.HookSupport.UNAVAILABLE;
                boolean hookConfigured = component.hasHook() && selected(component.hookKey);
                boolean canRestore = state == TelemetryPackages.State.NOT_INSTALLED && hasBackup;
                TelemetryPackages.Mode current = TelemetryPackages.selectedMode(state, hookConfigured, hasBackup);
                for (java.util.Map.Entry<TelemetryPackages.Mode, TextView> entry : controls.entrySet()) {
                    TelemetryPackages.Mode mode = entry.getKey();
                    TextView button = entry.getValue();
                    boolean show = mode != TelemetryPackages.Mode.HOOK || hookSupported;
                    button.setVisibility(show ? View.VISIBLE : View.GONE);
                    styleSegment(button, mode == current);
                    button.setEnabled((state.canChange || canRestore) && !TelemetryPackages.busy()
                            && mode != current && show);
                    button.setContentDescription(component.title + "，" + mode.label
                            + (mode == current ? "，当前选择" : ""));
                }
                String message = canRestore ? "已卸载 · 有 APK 备份" : state.label;
                if (TelemetryPackages.changing(component)) message = "正在修改…";
                else if (state == TelemetryPackages.State.ENABLED && hookConfigured) {
                    if (!hookSupported) message = compatibility ? "未匹配到拦截入口" : "此版本的拦截未适配";
                    else if (Settings.remote == null) message = "拦截待 LSPosed 连接";
                    else if (!selected("master")) message = "拦截待开启总开关";
                    else if (Settings.scopes != null && !Settings.scopes.contains(component.packageName))
                        message = "拦截待勾选 LSPosed 作用域";
                    else message = "已配置 Hook 拦截";
                } else if (state.canChange && component.hasHook() && !hookSupported) {
                    message += compatibility ? " · 未匹配到拦截入口" : " · 此版本的拦截未适配";
                }
                if (support == TelemetryPackages.HookSupport.EXPERIMENTAL) {
                    message += " · 实验兼容，已匹配拦截入口";
                }
                status.setText(message);
            });
        }
        refreshers.add(() -> hint.setVisibility(rows.stream().anyMatch(row -> row.getVisibility() == View.VISIBLE)
                ? View.VISIBLE : View.GONE));
    }

    private void keySettings(LinearLayout parent) {
        boolean matched=KeyCompatibility.matched();
        boolean allowed=matched&&(RuntimeProfile.knownSystem()||selected(Compatibility.KEY));
        label(parent,matched
                ? "系统按键入口已匹配，首次加载需重启手机。"
                : "当前系统按键入口未匹配。",12,ACCENT,false);
        toggle(parent,"启用自定义按键","key_actions",
                "需要系统框架作用域。音量多击判定会增加等待时间。",allowed);
        LinearLayout controls=vertical();
        parent.addView(controls);
        keyControls(controls,allowed);
    }

    private void keyControls(LinearLayout parent,boolean allowed) {
        parent.removeAllViews();
        int savedCode=ui.getInt("key_action_code",26);
        final int code=savedCode==24||savedCode==25?savedCode:26;
        String savedState=ui.getString("key_action_state","unlocked");
        final String state=java.util.Arrays.asList(KeyBindings.STATES).contains(savedState)?savedState:"unlocked";
        LinearLayout keys=new LinearLayout(this);
        parent.addView(keys,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<KeyBindings.CODES.length;i++) {
            int value=KeyBindings.CODES[i];
            keySegment(keys,KeyBindings.KEYS[i],code==value,()->{
                ui.edit().putInt("key_action_code",value).apply();
                keyControls(parent,allowed);
            });
        }
        LinearLayout states=new LinearLayout(this);
        LinearLayout.LayoutParams stateParams=new LinearLayout.LayoutParams(-1,-2);
        stateParams.topMargin=gap(8);stateParams.bottomMargin=gap(10);
        parent.addView(states,stateParams);
        String[] labels={"亮屏解锁","亮屏锁屏","息屏"};
        for(int i=0;i<KeyBindings.STATES.length;i++) {
            String value=KeyBindings.STATES[i];
            keySegment(states,labels[i],state.equals(value),()->{
                ui.edit().putString("key_action_state",value).apply();
                keyControls(parent,allowed);
            });
        }
        KeyBindings bindings=KeyBindings.read(Settings.local(this));
        for(int i=0;i<KeyBindings.GESTURES.length;i++) {
            String gesture=KeyBindings.GESTURES[i];
            org.json.JSONObject action=bindings.action(code,state,gesture);
            LinearLayout row=vertical();
            row.setPadding(0,gap(10),0,gap(10));
            LinearLayout heading=new LinearLayout(this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            TextView name=new TextView(this);
            name.setText(KeyBindings.GESTURE_LABELS[i]);name.setTextSize(15);name.setTextColor(INK);
            heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView current=new TextView(this);
            current.setText((action==null?"跟随系统":action.optString("title","未命名动作"))+"  ›");
            current.setTextSize(13);current.setTextColor(allowed?ACCENT:MUTED);
            current.setMaxWidth(dp(210));current.setMaxLines(2);
            heading.addView(current,new LinearLayout.LayoutParams(-2,-2));
            row.addView(heading);
            label(row,"系统绑定 · "+KeyBindings.original(this,code,state,gesture),12,MUTED,false);
            row.setMinimumHeight(dp(58));row.setFocusable(true);row.setEnabled(allowed);
            row.setContentDescription(KeyBindings.GESTURE_LABELS[i]+"，"+current.getText());
            row.setOnClickListener(v->chooseKeyAction(code,state,gesture));
            parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private void keySegment(LinearLayout parent,String title,boolean selected,Runnable action) {
        TextView option = segmentButton(parent, title, action);
        styleSegment(option, selected);
        option.setContentDescription(title + (selected ? "，当前选择" : "，点击切换"));
    }

    private TextView segmentButton(LinearLayout parent, String title, Runnable action) {
        TextView option = new TextView(this);
        option.setText(title);
        option.setTextSize(13);
        option.setGravity(Gravity.CENTER);
        option.setMinHeight(dp(44));
        option.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), gap(3), dp(2), gap(3));
        parent.addView(option, params);
        option.setOnClickListener(v -> action.run());
        return option;
    }

    private void styleSegment(TextView option, boolean selected) {
        option.setTextColor(selected ? Color.WHITE : INK);
        option.setBackground(background(selected ? ACCENT : 0xfff1f2ec, 10));
        option.setSelected(selected);
    }

    private void chooseKeyAction(int code,String state,String gesture) {
        chooseKeyApp(code,state,gesture);
    }

    private void saveKeyAction(int code,String state,String gesture,org.json.JSONObject action) {
        KeyBindings bindings=KeyBindings.read(Settings.local(this));
        bindings.put(code,state,gesture,action);
        Settings.setText(this,"key_bindings",bindings.encode());
        restoredScroll=scroll.getScrollY();
        render();
        Toast.makeText(this,selected("key_actions")?"动作已保存":"动作已保存，开启自定义按键后生效",Toast.LENGTH_SHORT).show();
    }

    private void chooseKeyApp(int code,String state,String gesture) {
        PackageManager pm=getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> available=pm.queryIntentActivities(query,PackageManager.GET_META_DATA);
        available.sort(new android.content.pm.ResolveInfo.DisplayNameComparator(pm));
        java.util.LinkedHashMap<String,android.content.pm.ResolveInfo> apps=new java.util.LinkedHashMap<>();
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        for(android.content.pm.ResolveInfo app:available) {
            if(!app.activityInfo.exported||!seen.add(app.activityInfo.packageName))continue;
            String title=app.loadLabel(pm)+"\n"+app.activityInfo.packageName;
            apps.put(title,app);
        }
        LinearLayout panel=vertical();
        panel.setPadding(dp(20),dp(4),dp(20),0);
        EditText search=new EditText(this);
        search.setSingleLine(true);search.setHint("搜索应用名称或包名");
        search.setContentDescription("搜索应用");
        panel.addView(search);
        LinearLayout defaults=new LinearLayout(this);
        panel.addView(defaults,new LinearLayout.LayoutParams(-1,-2));
        TextView system=new TextView(this);
        system.setText("跟随系统");system.setTextSize(14);system.setTextColor(ACCENT);
        clickable(system);
        defaults.addView(system,new LinearLayout.LayoutParams(0,-2,1));
        TextView none=new TextView(this);
        none.setText("不执行动作");none.setTextSize(14);none.setTextColor(ACCENT);
        clickable(none);
        defaults.addView(none,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ListView list=new android.widget.ListView(this);
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,new ArrayList<>(apps.keySet()));
        list.setAdapter(adapter);
        panel.addView(list,new LinearLayout.LayoutParams(-1,dp(330)));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("选择应用或动作")
                .setView(panel).setNegativeButton("取消",null).create();
        system.setOnClickListener(v->{dialog.dismiss();saveKeyAction(code,state,gesture,null);});
        none.setOnClickListener(v->{dialog.dismiss();saveKeyAction(code,state,gesture,KeyBindings.make("none","不执行动作",null));});
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){adapter.getFilter().filter(value);}
            public void afterTextChanged(android.text.Editable value){}
        });
        list.setOnItemClickListener((view,row,position,id)->{
            android.content.pm.ResolveInfo app=apps.get(adapter.getItem(position));
            dialog.dismiss();
            if(app!=null)chooseAppFunction(code,state,gesture,app);
        });
        dialog.show();
    }

    private void chooseAppFunction(int code,String state,String gesture,android.content.pm.ResolveInfo app) {
        String pkg=app.activityInfo.packageName;
        String appName=app.loadLabel(getPackageManager()).toString();
        List<String> titles=new ArrayList<>();
        List<org.json.JSONObject> actions=new ArrayList<>();
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
        try {
            if(launch!=null) {
                launch=KeyBindings.checkedIntent(this,launch,pkg);
                titles.add("打开应用");
                actions.add(KeyBindings.make("intent",appName+" · 打开应用",launch));
            }
            readStaticShortcuts(app,appName,titles,actions);
            if(pkg.equals("com.android.soundrecorder")) {
                titles.add("直接开始录音（系统快捷入口）");
                actions.add(KeyBindings.make("recorder",appName+" · 直接开始录音",null));
            }
        }catch(RuntimeException error) {
            Toast.makeText(this,error.getMessage(),Toast.LENGTH_SHORT).show();
        }
        List<android.content.pm.ResolveInfo> creators=getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg),0);
        creators.removeIf(item->!item.activityInfo.exported);
        if(!creators.isEmpty()) {
            titles.add("创建应用快捷方式…");
            actions.add(null);
        }
        if(titles.isEmpty()) {
            Toast.makeText(this,"未找到可用的应用入口或快捷方式",Toast.LENGTH_LONG).show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setTitle(appName)
                .setItems(titles.toArray(new String[0]),(dialog,index)->{
                    org.json.JSONObject action=actions.get(index);
                    if(action!=null)saveKeyAction(code,state,gesture,action);
                    else chooseShortcutCreator(code,state,gesture,pkg,creators);
                }).setNegativeButton("取消",null).show();
    }

    private void readStaticShortcuts(android.content.pm.ResolveInfo app,String appName,
                                     List<String> titles,List<org.json.JSONObject> actions) {
        android.content.res.XmlResourceParser parser=null;
        try {
            PackageManager pm=getPackageManager();
            android.content.pm.ActivityInfo activity=pm.getActivityInfo(
                    new ComponentName(app.activityInfo.packageName,app.activityInfo.name),PackageManager.GET_META_DATA);
            parser=activity.loadXmlMetaData(pm,"android.app.shortcuts");
            if(parser==null)return;
            android.content.res.Resources resources=pm.getResourcesForApplication(activity.applicationInfo);
            String ns="http://schemas.android.com/apk/res/android";
            String title=null;
            Intent shortcut=null;
            boolean enabled=false;
            int intents=0;
            while(parser.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(parser.getEventType()==org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if(parser.getName().equals("shortcut")) {
                        int label=parser.getAttributeResourceValue(ns,"shortcutShortLabel",0);
                        title=label==0?parser.getAttributeValue(ns,"shortcutId"):resources.getString(label);
                        enabled=parser.getAttributeBooleanValue(ns,"enabled",true);
                        shortcut=null;intents=0;
                    }else if(parser.getName().equals("intent")) {
                        shortcut=Intent.parseIntent(resources,parser,android.util.Xml.asAttributeSet(parser));
                        intents++;
                    }
                }else if(parser.getEventType()==org.xmlpull.v1.XmlPullParser.END_TAG
                        &&parser.getName().equals("shortcut")&&enabled&&intents==1&&shortcut!=null) {
                    try {
                        Intent checked=KeyBindings.checkedIntent(this,shortcut,activity.packageName);
                        titles.add(title==null?"应用快捷方式":title);
                        actions.add(KeyBindings.make("intent",appName+" · "+title,checked));
                    }catch(IllegalArgumentException ignored) {
                        // A shortcut with no exported destination cannot be launched by this app.
                    }
                }
            }
        }catch(Exception error) {
            android.util.Log.i("HyperPriv","App shortcut metadata unavailable: "+error.getClass().getSimpleName());
        }finally {
            if(parser!=null)parser.close();
        }
    }

    private void chooseShortcutCreator(int code,String state,String gesture,String pkg,
                                       List<android.content.pm.ResolveInfo> creators) {
        String[] names=new String[creators.size()];
        for(int i=0;i<names.length;i++)names[i]=creators.get(i).loadLabel(getPackageManager()).toString();
        new android.app.AlertDialog.Builder(this).setTitle("选择快捷方式提供方")
                .setItems(names,(dialog,index)->{
                    android.content.pm.ActivityInfo provider=creators.get(index).activityInfo;
                    pendingKeyShortcut=new String[]{String.valueOf(code),state,gesture,pkg};
                    Intent intent=new Intent(Intent.ACTION_CREATE_SHORTCUT)
                            .setComponent(new ComponentName(provider.packageName,provider.name));
                    try{startActivityForResult(intent,KEY_SHORTCUT_REQUEST);}
                    catch(ActivityNotFoundException|SecurityException error){
                        pendingKeyShortcut=null;
                        Toast.makeText(this,"此应用无法创建快捷方式",Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("取消",null).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=KEY_SHORTCUT_REQUEST)return;
        String[] target=pendingKeyShortcut;
        pendingKeyShortcut=null;
        if(result!=RESULT_OK||data==null||target==null)return;
        try {
            Intent shortcut=data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            if(shortcut==null)throw new IllegalArgumentException("应用未返回可调用的快捷方式");
            shortcut=KeyBindings.checkedIntent(this,shortcut,target[3]);
            String title=data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            if(title==null||title.trim().isEmpty())title="应用快捷方式";
            saveKeyAction(Integer.parseInt(target[0]),target[1],target[2],KeyBindings.make("intent",title,shortcut));
        }catch(RuntimeException error) {
            Toast.makeText(this,"快捷方式无法保存："+error.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    private void toggle(LinearLayout parent,String title,String key,String note,boolean available){
        if(RuntimeProfile.state().hidden(key))return;
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
                ExtraFeatures.Feature feature=ExtraFeatures.get(key);
                boolean systemOption=key.equals("master")||key.startsWith("core_")||(feature!=null&&feature.pkg.equals("android"));
                Toast.makeText(this,Settings.remote==null?"已保存到本机，连接框架后同步":key.equals(Compatibility.KEY)?"已保存，请重启目标应用；系统层兼容模式变化需重启手机":systemOption?"已保存；请重启手机使系统框架设置生效":"已保存，请重新启动对应应用进程",Toast.LENGTH_SHORT).show();
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
        LinearLayout branch=new LinearLayout(this);branch.setOrientation(LinearLayout.HORIZONTAL);branch.setPadding(dp(8),0,0,0);
        LinearLayout.LayoutParams branchParams=new LinearLayout.LayoutParams(-1,-2);branchParams.topMargin=gap(2);branchParams.bottomMargin=gap(6);parent.addView(branch,branchParams);
        View guide=new View(this);guide.setBackground(background(0xffd6e2cc,1));guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        branch.addView(guide,new LinearLayout.LayoutParams(dp(2),-1));
        LinearLayout body=vertical();body.setPadding(dp(12),0,0,0);branch.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        fold(header,branch,"info_"+id,title);return body;
    }
    private void link(LinearLayout parent,String label,String url){
        TextView target=label(parent,label+"  ↗",15,ACCENT,true);clickable(target);target.setContentDescription(label+"，在浏览器打开");
        target.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
            catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show();}});
    }
    private void aboutCard(){
        LinearLayout box=appGroup("about","关于");
        {
            LinearLayout eu=information(box,"eu_profile","本机已隐藏的项目");
            label(eu,"标准版与 EU 都自动检查组件和功能入口；确认不存在的选项隐藏，保留原设置。EU 另隐藏已确认由 ROM 处理的功能。读取失败时保留选项。",12,MUTED,false);
            for(String key:Options.KEYS)if(RuntimeProfile.state().hidden(key))label(eu,UiCatalog.label(key)+"："+RuntimeProfile.state().reason(key),12,MUTED,false);
        }
        launcherIconControl(box);
        toggle(box,"实验兼容模式",Compatibility.KEY,"默认开启，可手动关闭。开启后放宽机型、版本及设置构建校验，尝试现有规则，沿用各功能当前开关。方法匹配不代表行为正确；缺失方法跳过，不会补齐尚未实现的功能。切换后重启目标进程，系统层需重启手机。",true);
        link(box,"检查更新","https://github.com/tiancaiProgramer/HyperPriv/releases");
        label(box,"tiancaiProgramer/HyperPriv · 安装包与更新说明",12,MUTED,false);
        LinearLayout usage=information(box,"usage","使用与恢复");
        label(usage,"开关开启表示执行对应调整；关闭单项后恢复原方法。关闭总开关不会清除独立设置。",12,MUTED,false);
        label(usage,"应用功能需勾选对应作用域；自启动与剪贴板提示需要权限管理作用域。系统层功能还需勾选系统框架，修改选项后重启手机。",12,MUTED,false);
        label(usage,"停用或卸载本模块后重启目标进程，也可恢复原方法。手动开启的 USB 设置或选择的锁屏时间需自行恢复。",12,MUTED,false);
        TextView disable=label(usage,"关闭模块总开关",14,ACCENT,true);clickable(disable);
        disable.setOnClickListener(v->{Settings.set(this,"master",false);refresh();Toast.makeText(this,"总开关已关闭，独立设置已保留",Toast.LENGTH_SHORT).show();});
        LinearLayout versions=information(box,"versions","组件适配说明");
        for(UiCatalog.Component app:UiCatalog.COMPONENTS){
            if(!RuntimeProfile.state().needsScope(app.pkg))continue;
            label(versions,app.title,14,INK,true);label(versions,appState(app.pkg).text,12,MUTED,false);
            label(versions,"已核对版本 "+UiCatalog.versions(app.pkg),12,MUTED,false);
        }
        label(versions,"默认模式另核对设置安装包构建；实验兼容模式放宽此校验。这里的版本信息不代表规则已实际命中。",12,MUTED,false);
        LinearLayout limits=information(box,"limits","验证进度与功能边界");
        label(limits,"本次 EU 适配已核对方法定义与隔离回调，实际业务仍需测试；此前普通版的安装、读秒、导航和截图记录保留原验证范围。",12,MUTED,false);
        label(limits,"方法匹配、入口命中与实际业务对照分别记录；实验兼容不保证未知机型生效。完整状态见功能说明。",12,MUTED,false);
        label(limits,"新增系统层功能默认关闭。没有已适配业务入口的安全守护 Root 选项已隐藏，原设置仍保留。",12,MUTED,false);
        link(limits,"查看逐项功能说明","https://github.com/tiancaiProgramer/HyperPriv/blob/main/FEATURES.md");
        LinearLayout source=information(box,"source","项目与许可");
        link(source,"HyperPriv 源码","https://github.com/tiancaiProgramer/HyperPriv");
        link(source,"参考项目 HyperCeiler","https://github.com/ReChronoRain/HyperCeiler");
        label(source,"AGPL-3.0 · 模块无网络权限。界面参考 NoAdMi；核心破解入口参考 HyperCeiler 与 CorePatch 的设计。",12,MUTED,false);
    }
}

